import argparse
import json
import os
import re
import sys
import urllib.request
from pathlib import Path
from typing import Any

from dotenv import load_dotenv


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.config import ROOT_DIR
from backend.app.database import SessionLocal
from backend.app.models import Episode, EpisodeExperienceConfig, Highlight


load_dotenv(ROOT_DIR / ".env")


EXPERIENCE_FIXTURE_PATH = ROOT / "backend" / "app" / "fixtures" / "experience_configs.json"

THEME_PRESETS = {
    "road": {
        "theme_key": "road",
        "name": "北往·返乡烟火",
        "class_name": "theme-road",
        "badge": "返乡烟火 · 年三十",
        "signal": "讨薪现金 / 家用电话 / 路边烟雾 / 行李摩托",
        "accent": "#ff7a30",
        "soft": "#f2e2c4",
        "endpoint_label": "家",
    },
    "xianxia": {
        "theme_key": "xianxia",
        "name": "云渺·灵雨压场",
        "class_name": "theme-xianxia",
        "badge": "灵雨入门 · 身份悬念",
        "signal": "雨伞 / 周家老宅 / 病榻 / 神秘访客",
        "accent": "#8bd3ff",
        "soft": "#e7c6ff",
        "endpoint_label": "灵",
    },
    "treasure": {
        "theme_key": "treasure",
        "name": "北派寻宝·机关线索",
        "class_name": "theme-treasure",
        "badge": "寻宝线索 · 机关风险",
        "signal": "地图 / 罗盘 / 机关 / 悬念线索",
        "accent": "#d6b45f",
        "soft": "#55d68f",
        "endpoint_label": "宝",
    },
    "winter": {
        "theme_key": "winter",
        "name": "那年冬至·雪夜心事",
        "class_name": "theme-winter",
        "badge": "冬日情绪 · 爱情拉扯",
        "signal": "雪夜 / 回忆 / 心动 / 遗憾",
        "accent": "#9fd8ff",
        "soft": "#ff9bbd",
        "endpoint_label": "心",
    },
    "city": {
        "theme_key": "city",
        "name": "都市情绪场",
        "class_name": "theme-city",
        "badge": "默认互动主题",
        "signal": "高光时间轴 / 弹幕反馈 / 互动按钮",
        "accent": "#ff4f64",
        "soft": "#12d6b0",
        "endpoint_label": "",
    },
}

ALLOWED_ASSETS = {
    "road": [
        "mealSteam",
        "noPayBill",
        "goSign",
        "wageStamp",
        "debtCash",
        "homePhone",
        "homeTicket",
        "homeLantern",
        "smokeQuestion",
        "roadQuestion",
        "rockWord",
        "rockMoto",
        "northTitle",
    ],
    "xianxia": ["xianxiaRain", "xianxiaSeal", "xianxiaSpirit", "question", "tear"],
    "treasure": ["treasureMap", "treasureCompass", "treasureTrap", "question", "charge"],
    "winter": ["winterSnow", "winterHeart", "winterMemory", "tear", "laugh"],
    "city": ["charge", "question", "rock", "laugh", "tear"],
}

SYSTEM_PROMPT = """
你是短剧互动体验策略师，负责把短剧标题、人工高光、字幕/画面备注转成可落地的移动端互动体验配置。

必须只输出 JSON 对象，不要 Markdown，不要解释。
必须遵守：
1. 贴图 asset_ids 只能从 allowed_assets 中选择。
2. sticker_timeline 必须按视频时间顺序排列，不能在无关时间乱出现。
3. 每个贴图时间窗要解释 meaning，说明为什么贴合当前剧情。
4. 弹幕模式必须分 light / carnival / immerse 三档。
5. 不要输出 API Key、接入点或任何密钥内容。
6. 输出必须适合保存到服务端 episode_experience_configs.config 字段。
""".strip()


def theme_key_for_title(title: str) -> str:
    if "北往" in title:
        return "road"
    if "云渺" in title or "修仙" in title:
        return "xianxia"
    if "寻宝" in title:
        return "treasure"
    if "冬至" in title:
        return "winter"
    return "city"


def extract_json(text: str) -> dict[str, Any]:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?", "", text).strip()
        text = re.sub(r"```$", "", text).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, re.S)
        if not match:
            raise
        return json.loads(match.group(0))


def annotation_input_path(episode_id: int) -> Path:
    return ROOT / "data" / "annotation_inputs" / f"episode_{episode_id}.json"


def highlight_payload(highlight: Highlight) -> dict[str, Any]:
    try:
        options = json.loads(highlight.options_json)
    except json.JSONDecodeError:
        options = []
    return {
        "start_time_sec": highlight.start_time_sec,
        "end_time_sec": highlight.end_time_sec,
        "title": highlight.title,
        "description": highlight.description,
        "highlight_type": highlight.highlight_type,
        "emotion": highlight.emotion,
        "source": highlight.source,
        "confidence": highlight.confidence,
        "annotation_reason": highlight.annotation_reason,
        "evidence_text": highlight.evidence_text,
        "options": options,
    }


def load_context_by_episode_id(episode_id: int) -> dict[str, Any]:
    with SessionLocal() as db:
        episode = db.get(Episode, episode_id)
        if not episode:
            raise SystemExit(f"剧集不存在：{episode_id}")
        highlights = (
            db.query(Highlight)
            .filter(Highlight.episode_id == episode_id)
            .order_by(Highlight.start_time_sec.asc())
            .all()
        )
        context = {
            "episode_id": episode.id,
            "drama_title": episode.drama.title,
            "episode_title": episode.title,
            "episode_no": episode.episode_no,
            "duration_sec": episode.duration_sec,
            "video_path": episode.video_path,
            "segments": [],
            "reviewed_highlights": [highlight_payload(item) for item in highlights],
        }

    input_path = annotation_input_path(episode_id)
    if input_path.exists():
        payload = json.loads(input_path.read_text(encoding="utf-8"))
        context["segments"] = payload.get("segments", [])
    return context


def build_prompt(context: dict[str, Any]) -> str:
    theme_key = theme_key_for_title(context.get("drama_title", ""))
    return json.dumps(
        {
            "task": "生成 episode_experience_configs.config，可直接保存到服务端。",
            "theme_preset": THEME_PRESETS[theme_key],
            "allowed_assets": ALLOWED_ASSETS[theme_key],
            "output_schema": {
                "player_theme": THEME_PRESETS[theme_key],
                "sticker_timeline": [
                    {
                        "start_time_sec": 0,
                        "end_time_sec": 0,
                        "asset_ids": ["只能使用 allowed_assets 中的值"],
                        "cadence_sec": 6,
                        "burst_count": 3,
                        "meaning": "为什么这个时间窗应该出现这些贴图",
                    }
                ],
                "danmaku_modes": {
                    "light": {"label": "轻聊", "enabled": True, "density": 0.7, "include_modes": ["light"]},
                    "carnival": {
                        "label": "狂欢",
                        "enabled": True,
                        "density": 1,
                        "include_modes": ["light", "curated", "seed", "carnival"],
                    },
                    "immerse": {"label": "沉浸", "enabled": False, "density": 0, "include_modes": []},
                },
                "draft_notes": ["人工复核时需要注意的点"],
            },
            "episode": context,
        },
        ensure_ascii=False,
    )


def default_danmaku_modes() -> dict[str, Any]:
    return {
        "light": {"label": "轻聊", "enabled": True, "density": 0.72, "include_modes": ["light"]},
        "carnival": {
            "label": "狂欢",
            "enabled": True,
            "density": 1,
            "include_modes": ["light", "curated", "seed", "carnival"],
        },
        "immerse": {"label": "沉浸", "enabled": False, "density": 0, "include_modes": []},
    }


def normalize_danmaku_modes(raw_modes: dict[str, Any] | None) -> dict[str, Any]:
    defaults = default_danmaku_modes()
    if not isinstance(raw_modes, dict):
        return defaults
    normalized = {}
    for key, fallback in defaults.items():
        raw = raw_modes.get(key) if isinstance(raw_modes.get(key), dict) else {}
        include_modes = raw.get("include_modes", raw.get("includeModes", fallback["include_modes"]))
        if not isinstance(include_modes, list):
            include_modes = fallback["include_modes"]
        enabled = bool(raw.get("enabled", fallback["enabled"]) and include_modes)
        if key == "immerse":
            enabled = False
            include_modes = []
        normalized[key] = {
            "label": raw.get("label", fallback["label"]),
            "enabled": enabled,
            "density": float(raw.get("density", fallback["density"])),
            "include_modes": include_modes,
        }
    return normalized


def fallback_timeline(context: dict[str, Any], theme_key: str) -> list[dict[str, Any]]:
    highlights = context.get("reviewed_highlights") or []
    assets = ALLOWED_ASSETS[theme_key]
    rows = []
    for index, highlight in enumerate(highlights[:4]):
        rows.append(
            {
                "start_time_sec": float(highlight.get("start_time_sec", 0)),
                "end_time_sec": float(highlight.get("end_time_sec", highlight.get("start_time_sec", 0) + 8)),
                "asset_ids": assets[index % len(assets) : index % len(assets) + 2] or assets[:2],
                "cadence_sec": 6,
                "burst_count": 3,
                "meaning": highlight.get("title") or highlight.get("description") or "根据高光点生成的体验草稿。",
            }
        )
    if rows:
        return rows
    duration = float(context.get("duration_sec") or 180)
    return [
        {
            "start_time_sec": round(duration * 0.18, 2),
            "end_time_sec": round(duration * 0.32, 2),
            "asset_ids": assets[:2],
            "cadence_sec": 7,
            "burst_count": 2,
            "meaning": "根据剧名和题材生成的氛围体验草稿，需人工复核。",
        },
        {
            "start_time_sec": round(duration * 0.58, 2),
            "end_time_sec": round(duration * 0.76, 2),
            "asset_ids": assets[-2:],
            "cadence_sec": 6,
            "burst_count": 3,
            "meaning": "根据剧名和题材生成的高光体验草稿，需人工复核。",
        },
    ]


def fallback_config(context: dict[str, Any]) -> dict[str, Any]:
    theme_key = theme_key_for_title(context.get("drama_title", ""))
    return {
        "player_theme": THEME_PRESETS[theme_key],
        "sticker_timeline": fallback_timeline(context, theme_key),
        "danmaku_modes": default_danmaku_modes(),
        "draft_notes": ["本配置由本地兜底规则生成，需要人工复核。"],
    }


def normalize_timeline(config: dict[str, Any], context: dict[str, Any], theme_key: str) -> list[dict[str, Any]]:
    allowed = set(ALLOWED_ASSETS[theme_key])
    rows = []
    for slot in config.get("sticker_timeline", []):
        asset_ids = [item for item in slot.get("asset_ids", slot.get("assets", [])) if item in allowed]
        if not asset_ids:
            continue
        start = float(slot.get("start_time_sec", slot.get("start", 0)))
        end = float(slot.get("end_time_sec", slot.get("end", start + 8)))
        if end <= start:
            end = min(float(context.get("duration_sec") or start + 8), start + 8)
        rows.append(
            {
                "start_time_sec": round(start, 2),
                "end_time_sec": round(end, 2),
                "asset_ids": asset_ids[:4],
                "cadence_sec": int(slot.get("cadence_sec", slot.get("cadenceSec", 6))),
                "burst_count": int(slot.get("burst_count", slot.get("burstCount", 3))),
                "meaning": slot.get("meaning", "大模型生成的体验时间窗。"),
            }
        )
    return sorted(rows, key=lambda item: item["start_time_sec"]) or fallback_timeline(context, theme_key)


def has_rich_segments(context: dict[str, Any]) -> bool:
    for segment in context.get("segments", []):
        text = " ".join(
            str(segment.get(key, "")) for key in ("subtitle_text", "visual_note", "manual_note") if segment.get(key)
        )
        if text.strip():
            return True
    return False


def nearest_highlight(context: dict[str, Any], start: float, end: float) -> dict[str, Any] | None:
    highlights = context.get("reviewed_highlights") or []
    best = None
    best_overlap = 0.0
    for highlight in highlights:
        h_start = float(highlight.get("start_time_sec", 0))
        h_end = float(highlight.get("end_time_sec", h_start))
        overlap = max(0.0, min(end, h_end) - max(start, h_start))
        if overlap > best_overlap:
            best = highlight
            best_overlap = overlap
    return best


def conservative_meaning(context: dict[str, Any], theme_key: str, slot: dict[str, Any]) -> str:
    start = float(slot["start_time_sec"])
    end = float(slot["end_time_sec"])
    highlight = nearest_highlight(context, start, end)
    assets = " / ".join(slot.get("asset_ids", []))
    if highlight:
        return f"基于当前高光《{highlight.get('title', '待复核高光')}》生成的{THEME_PRESETS[theme_key]['name']}体验草稿，贴图 {assets} 需要人工对齐正片画面。"
    return f"基于《{context.get('drama_title')}》题材生成的氛围体验草稿，贴图 {assets} 仅作为待复核建议，不代表已确认画面内容。"


def normalize_config(raw: dict[str, Any], context: dict[str, Any]) -> dict[str, Any]:
    theme_key = theme_key_for_title(context.get("drama_title", ""))
    theme = {**THEME_PRESETS[theme_key], **(raw.get("player_theme") or {})}
    theme["theme_key"] = theme_key
    theme["class_name"] = THEME_PRESETS[theme_key]["class_name"]
    timeline = normalize_timeline(raw, context, theme_key)
    notes = raw.get("draft_notes", [])
    if not has_rich_segments(context):
        notes = [
            "该剧集暂无字幕/画面备注输入，本配置仅基于剧名和现有高光草稿生成，必须人工复核时间点和剧情语义。",
            *notes,
        ]
        for slot in timeline:
            slot["meaning"] = conservative_meaning(context, theme_key, slot)
    return {
        "player_theme": theme,
        "sticker_timeline": timeline,
        "danmaku_modes": normalize_danmaku_modes(raw.get("danmaku_modes")),
        "draft_notes": notes,
    }


def call_llm(prompt: str) -> dict[str, Any]:
    api_key = os.getenv("ARK_API_KEY")
    model = os.getenv("ARK_MODEL") or os.getenv("ARK_ENDPOINT_ID")
    base_url = os.getenv("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/")
    if not api_key or not model:
        raise RuntimeError("missing_env")
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.5,
    }
    request = urllib.request.Request(
        f"{base_url}/chat/completions",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        data = json.loads(response.read().decode("utf-8"))
    return extract_json(data["choices"][0]["message"]["content"])


def build_experience_payload(context: dict[str, Any]) -> dict[str, Any]:
    try:
        config = normalize_config(call_llm(build_prompt(context)), context)
        source = "llm_seed"
        fallback_reason = None
    except Exception as exc:
        config = fallback_config(context)
        source = "local_fallback"
        fallback_reason = exc.__class__.__name__
    payload = {
        "episode_id": context["episode_id"],
        "drama_title": context["drama_title"],
        "episode_title": context["episode_title"],
        "episode_no": context["episode_no"],
        "version": 1,
        "source": source,
        "model_version": "episode-experience-v1",
        "review_status": "llm_draft" if source == "llm_seed" else "draft",
        "config": config,
    }
    if fallback_reason:
        payload["fallback_reason"] = fallback_reason
    return payload


def upsert_experience_config(payload: dict[str, Any], overwrite: bool) -> None:
    with SessionLocal() as db:
        row = (
            db.query(EpisodeExperienceConfig)
            .filter(EpisodeExperienceConfig.episode_id == int(payload["episode_id"]))
            .first()
        )
        if row and not overwrite:
            return
        if not row:
            row = EpisodeExperienceConfig(episode_id=int(payload["episode_id"]))
            db.add(row)
        row.version = int(payload.get("version", 1))
        row.source = payload.get("source", "llm_seed")
        row.model_version = payload.get("model_version", "episode-experience-v1")
        row.review_status = payload.get("review_status", "llm_draft")
        row.config_json = json.dumps(payload.get("config", {}), ensure_ascii=False)
        db.commit()


def update_fixture(payloads: list[dict[str, Any]]) -> None:
    if EXPERIENCE_FIXTURE_PATH.exists():
        fixture = json.loads(EXPERIENCE_FIXTURE_PATH.read_text(encoding="utf-8"))
    else:
        fixture = {"episodes": []}
    episodes = fixture.setdefault("episodes", [])
    for payload in payloads:
        replacement = {
            "drama_title": payload["drama_title"],
            "episode_no": payload["episode_no"],
            "version": payload.get("version", 1),
            "source": payload.get("source", "llm_seed"),
            "model_version": payload.get("model_version", "episode-experience-v1"),
            "review_status": payload.get("review_status", "llm_draft"),
            "config": payload["config"],
        }
        for index, item in enumerate(episodes):
            if item.get("drama_title") == replacement["drama_title"] and int(item.get("episode_no")) == int(
                replacement["episode_no"]
            ):
                episodes[index] = replacement
                break
        else:
            episodes.append(replacement)
    EXPERIENCE_FIXTURE_PATH.write_text(json.dumps(fixture, ensure_ascii=False, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="生成剧集互动体验配置。")
    parser.add_argument("--episode-id", type=int, action="append", default=[], help="数据库中的剧集 ID，可重复传入。")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "frontend" / "assets" / "themes")
    parser.add_argument("--save-db", action="store_true", help="写入 episode_experience_configs 表。")
    parser.add_argument("--update-fixture", action="store_true", help="同步写入 fixture，便于重建数据库。")
    parser.add_argument("--overwrite", action="store_true", help="覆盖已有配置。")
    args = parser.parse_args()

    if not args.episode_id:
        raise SystemExit("至少传入一个 --episode-id")

    args.output_dir.mkdir(parents=True, exist_ok=True)
    payloads = []
    for episode_id in args.episode_id:
        context = load_context_by_episode_id(episode_id)
        payload = build_experience_payload(context)
        payloads.append(payload)
        output_path = args.output_dir / f"episode_{episode_id}_experience_config.json"
        output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        if args.save_db:
            upsert_experience_config(payload, args.overwrite)
        print(json.dumps({"episode_id": episode_id, "source": payload["source"], "output": str(output_path)}, ensure_ascii=False))

    if args.update_fixture:
        update_fixture(payloads)


if __name__ == "__main__":
    main()
