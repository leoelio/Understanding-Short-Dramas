import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from dotenv import load_dotenv


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.config import ROOT_DIR
from backend.app.database import SessionLocal
from backend.app.models import Episode, EpisodeExperienceConfig, Highlight
from backend.app.taxonomy import ALLOWED_EMOTIONS, HIGHLIGHT_TAXONOMY, normalize_highlight_type


load_dotenv(ROOT_DIR / ".env")

MODEL_VERSION = "highlight-sticker-upgrade-v1"
OUTPUT_DIR = ROOT / "data" / "llm_upgrades"

ASSET_CATALOG = [
    {"id": "mealSteam", "label": "打工人日常", "theme": "北往返乡"},
    {"id": "noPayBill", "label": "一分没结", "theme": "北往返乡"},
    {"id": "goSign", "label": "冲一下", "theme": "北往返乡"},
    {"id": "debtCash", "label": "欠薪结清", "theme": "北往返乡"},
    {"id": "homePhone", "label": "想家电话", "theme": "北往返乡"},
    {"id": "homeLantern", "label": "到家灯笼", "theme": "北往返乡"},
    {"id": "smokeQuestion", "label": "悬着一口烟", "theme": "北往返乡"},
    {"id": "wageStamp", "label": "欠薪得还", "theme": "北往返乡"},
    {"id": "homeTicket", "label": "回家票", "theme": "北往返乡"},
    {"id": "roadQuestion", "label": "回得去吗", "theme": "北往返乡"},
    {"id": "rockWord", "label": "贼摇滚", "theme": "北往返乡"},
    {"id": "rockMoto", "label": "摩托返乡", "theme": "北往返乡"},
    {"id": "northTitle", "label": "北往标题", "theme": "北往返乡"},
    {"id": "xianxiaRain", "label": "灵雨伞", "theme": "仙侠悬念"},
    {"id": "xianxiaSeal", "label": "法阵亮起", "theme": "仙侠悬念"},
    {"id": "xianxiaSpirit", "label": "灵气现形", "theme": "仙侠悬念"},
    {"id": "treasureMap", "label": "藏宝图", "theme": "寻宝机关"},
    {"id": "treasureCompass", "label": "罗盘指针", "theme": "寻宝机关"},
    {"id": "treasureTrap", "label": "机关警报", "theme": "寻宝机关"},
    {"id": "winterSnow", "label": "冬至雪", "theme": "冬至爱情"},
    {"id": "winterHeart", "label": "心事", "theme": "冬至爱情"},
    {"id": "winterMemory", "label": "旧照片", "theme": "冬至爱情"},
    {"id": "winterCrow", "label": "乌鸦无语", "theme": "冬至爱情"},
    {"id": "winterWow", "label": "哇塞", "theme": "冬至爱情"},
    {"id": "winterKiss", "label": "突然亲吻", "theme": "冬至爱情"},
    {"id": "winterChoice", "label": "选哪边", "theme": "冬至爱情"},
    {"id": "winterBrokenHeart", "label": "心碎", "theme": "冬至爱情"},
    {"id": "winterBlush", "label": "脸红", "theme": "冬至爱情"},
    {"id": "winterHeartbeat", "label": "心跳", "theme": "冬至爱情"},
    {"id": "winterHoldBack", "label": "别冲动", "theme": "冬至爱情"},
    {"id": "winterQuestionLove", "label": "爱或现实", "theme": "冬至爱情"},
    {"id": "winterWarmHug", "label": "抱抱", "theme": "冬至爱情"},
    {"id": "familyMedal", "label": "家族荣耀", "theme": "家族逆袭"},
    {"id": "familyTable", "label": "家里家外", "theme": "家庭烟火"},
    {"id": "divorceRose", "label": "离婚玫瑰", "theme": "都市情感"},
    {"id": "nightNeon", "label": "撕夜霓虹", "theme": "悬疑夜色"},
    {"id": "harvestSystem", "label": "系统满仓", "theme": "荒年系统"},
    {"id": "nobleMask", "label": "纨绔面具", "theme": "古风反转"},
    {"id": "vehicleTrain", "label": "火车", "theme": "交通选择"},
    {"id": "vehicleCar", "label": "小车", "theme": "交通选择"},
    {"id": "vehicleMotorcycle", "label": "摩托车", "theme": "交通选择"},
    {"id": "charge", "label": "冲", "theme": "通用情绪"},
    {"id": "question", "label": "问号", "theme": "通用情绪"},
    {"id": "laugh", "label": "好笑", "theme": "通用情绪"},
    {"id": "rock", "label": "摇滚", "theme": "通用情绪"},
    {"id": "tear", "label": "心疼", "theme": "通用情绪"},
]

ASSET_IDS = {item["id"] for item in ASSET_CATALOG}


def redact(text: str) -> str:
    text = re.sub(r"ark-[A-Za-z0-9-]+", "[redacted_api_key]", text)
    text = re.sub(r"sk-proj-[A-Za-z0-9_-]+", "[redacted_api_key]", text)
    return text


def read_json_field(value: str, fallback: Any) -> Any:
    try:
        return json.loads(value or "")
    except json.JSONDecodeError:
        return fallback


def write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(redact(json.dumps(payload, ensure_ascii=False, indent=2)), encoding="utf-8")


def annotation_input_path(episode_id: int) -> Path:
    return ROOT / "data" / "annotation_inputs" / f"episode_{episode_id}.json"


def load_segments(episode_id: int) -> list[dict[str, Any]]:
    path = annotation_input_path(episode_id)
    if not path.exists():
        return []
    payload = json.loads(path.read_text(encoding="utf-8"))
    rows = []
    for item in payload.get("segments", [])[:100]:
        rows.append(
            {
                "segment_id": item.get("segment_id"),
                "start_time_sec": item.get("start_time_sec"),
                "end_time_sec": item.get("end_time_sec"),
                "subtitle_text": item.get("subtitle_text", ""),
                "visual_note": item.get("visual_note", ""),
                "audio_note": item.get("audio_note", ""),
                "manual_note": item.get("manual_note", ""),
            }
        )
    return rows


def has_rich_segments(segments: list[dict[str, Any]]) -> bool:
    for item in segments:
        if any(str(item.get(key, "")).strip() for key in ("subtitle_text", "visual_note", "audio_note", "manual_note")):
            return True
    return False


def highlight_payload(highlight: Highlight) -> dict[str, Any]:
    return {
        "id": highlight.id,
        "start_time_sec": highlight.start_time_sec,
        "end_time_sec": highlight.end_time_sec,
        "title": highlight.title,
        "description": highlight.description,
        "highlight_type": highlight.highlight_type,
        "emotion": highlight.emotion,
        "options": read_json_field(highlight.options_json, []),
        "source": highlight.source,
        "model_version": highlight.model_version,
        "annotation_reason": highlight.annotation_reason,
        "evidence_text": highlight.evidence_text,
    }


def load_context(episode_id: int) -> dict[str, Any]:
    with SessionLocal() as db:
        episode = db.get(Episode, episode_id)
        if not episode:
            raise SystemExit(f"episode not found: {episode_id}")
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
            "has_rich_segments": False,
            "segments": load_segments(episode.id),
            "existing_highlights": [highlight_payload(item) for item in highlights],
        }
    context["has_rich_segments"] = has_rich_segments(context["segments"])
    return context


def allowed_taxonomy_payload() -> dict[str, Any]:
    return {
        "highlight_types": [
            {
                "label": item["label"],
                "description": item["description"],
                "interaction": item["interaction"],
                "aliases": item["aliases"],
                "emotions": item["emotions"],
            }
            for item in HIGHLIGHT_TAXONOMY
        ],
        "emotions": sorted(ALLOWED_EMOTIONS),
    }


def build_prompt(context: dict[str, Any]) -> str:
    return json.dumps(
        {
            "task": "升级现有短剧高光和特色贴图体验，输出可写回数据库的 JSON。",
            "hard_rules": [
                "必须保留 existing_highlights 里的每个 id，不要新增、删除或合并高光。",
                "不要修改 start_time_sec/end_time_sec；时间点已经由人工或系统锚定。",
                "如果 has_rich_segments=false，只能基于现有高光表达做文案升级，不要编造新剧情。",
                "如果 has_rich_segments=true，可以参考 segments 优化标题、描述、情绪和按钮，但仍不能脱离证据。",
                "按钮 label 必须 1-8 个中文字符，适合用户快速点击表达情绪。",
                "贴图 slots 必须按时间顺序，asset_ids 只能来自 asset_catalog。",
                "每集保持稀疏：通常 3-5 个贴图时间窗，不要整集乱飞。",
                "不要输出 API Key、接入点、环境变量或任何密钥内容。",
            ],
            "output_schema": {
                "highlight_updates": [
                    {
                        "id": 0,
                        "title": "更像产品高光名称的短标题",
                        "description": "面向观看体验的剧情理解描述",
                        "highlight_type": "必须来自 taxonomy.highlight_types.label",
                        "emotion": "必须来自 taxonomy.emotions",
                        "options": [{"key": "stable_key", "label": "短按钮"}],
                        "annotation_reason": "为什么这个高光值得触发互动",
                        "evidence_text": "保留或补充证据；没有上下文时说明基于现有高光锚点",
                    }
                ],
                "sticker_timeline": [
                    {
                        "start_time_sec": 0,
                        "end_time_sec": 8,
                        "asset_ids": ["asset_catalog id"],
                        "cadence_sec": 3,
                        "burst_count": 2,
                        "meaning": "贴图和当前剧情/情绪的对应关系",
                        "asset_prompt": "后续如用图像模型重绘贴纸，可使用的中文提示词",
                    }
                ],
                "draft_notes": ["人工复核时需要关注的点"],
            },
            "taxonomy": allowed_taxonomy_payload(),
            "asset_catalog": ASSET_CATALOG,
            "episode": context,
        },
        ensure_ascii=False,
    )


def extract_json(text: str) -> dict[str, Any]:
    text = redact(text.strip())
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


def call_llm(prompt: str) -> dict[str, Any]:
    api_key = os.getenv("ARK_API_KEY")
    model = os.getenv("ARK_MODEL") or os.getenv("ARK_ENDPOINT_ID")
    base_url = os.getenv("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/")
    if not api_key or not model:
        raise RuntimeError("missing_ark_env")
    body = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": "你是短剧互动产品的剧情理解与贴图策略专家。只输出 JSON，不输出解释。",
            },
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.35,
    }
    request = urllib.request.Request(
        f"{base_url}/chat/completions",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = redact(exc.read().decode("utf-8", errors="replace"))
        raise RuntimeError(f"llm_http_{exc.code}: {detail}") from exc
    return extract_json(data["choices"][0]["message"]["content"])


def option_key(label: str, index: int) -> str:
    ascii_key = re.sub(r"[^a-zA-Z0-9]+", "_", label).strip("_").lower()
    return ascii_key or f"option_{index + 1}"


def normalize_options(raw: Any, fallback: list[dict[str, Any]]) -> list[dict[str, str]]:
    source = raw if isinstance(raw, list) and raw else fallback
    rows = []
    seen = set()
    for index, item in enumerate(source or []):
        if isinstance(item, dict):
            label = str(item.get("label") or item.get("text") or item.get("name") or "").strip()
            key = str(item.get("key") or item.get("id") or option_key(label, index)).strip()
        else:
            label = str(item).strip()
            key = option_key(label, index)
        label = label[:8]
        if not label or not key or key in seen:
            continue
        seen.add(key)
        rows.append({"key": key[:64], "label": label})
        if len(rows) >= 4:
            break
    while len(rows) < 2:
        label = "想表达" if not rows else "再来"
        key = option_key(label, len(rows))
        rows.append({"key": key, "label": label})
    return rows


def normalize_update(raw: dict[str, Any], existing: dict[str, Any]) -> dict[str, Any]:
    highlight_type = normalize_highlight_type(str(raw.get("highlight_type") or existing["highlight_type"]))
    if highlight_type not in {item["label"] for item in HIGHLIGHT_TAXONOMY}:
        highlight_type = normalize_highlight_type(existing["highlight_type"])
    emotion = str(raw.get("emotion") or existing["emotion"]).strip()
    if emotion not in ALLOWED_EMOTIONS:
        emotion = existing["emotion"] if existing["emotion"] in ALLOWED_EMOTIONS else "期待"
    evidence = str(raw.get("evidence_text") or existing.get("evidence_text") or "").strip()
    if not evidence:
        evidence = "基于现有高光锚点升级互动表达；缺少逐段字幕或画面输入，需人工复核。"
    return {
        "id": int(existing["id"]),
        "title": str(raw.get("title") or existing["title"]).strip()[:80],
        "description": str(raw.get("description") or existing["description"]).strip()[:500],
        "highlight_type": highlight_type,
        "emotion": emotion,
        "options": normalize_options(raw.get("options"), existing.get("options", [])),
        "annotation_reason": str(raw.get("annotation_reason") or existing.get("annotation_reason") or "").strip()[:500],
        "evidence_text": evidence[:500],
    }


def fallback_asset_ids(text: str, drama_title: str) -> list[str]:
    picked: list[str] = []

    def add(*asset_ids: str) -> None:
        for asset_id in asset_ids:
            if asset_id in ASSET_IDS and asset_id not in picked:
                picked.append(asset_id)

    source = f"{drama_title} {text}"
    if "北往" in source:
        add("homeTicket", "roadQuestion", "rockMoto")
    if "冬至" in source or "爱情" in source or "心动" in source or "亲" in source:
        add("winterHeart", "winterBlush", "winterKiss")
    if "云渺" in source or "修仙" in source:
        add("xianxiaRain", "xianxiaSeal", "xianxiaSpirit")
    if "寻宝" in source:
        add("treasureMap", "treasureCompass", "treasureTrap")
    if "太奶" in source or "家族" in source:
        add("familyMedal", "charge", "question")
    if "家里家外" in source:
        add("familyTable", "tear", "laugh")
    if "离婚" in source or "相遇" in source:
        add("divorceRose", "winterHeart", "tear")
    if "撕夜" in source or "夜" in source:
        add("nightNeon", "question", "tear")
    if "荒年" in source or "系统" in source or "满仓" in source:
        add("harvestSystem", "charge", "laugh")
    if "纨绔" in source:
        add("nobleMask", "charge", "question")
    if "悬念" in source or "疑问" in source:
        add("question")
    if "心疼" in source or "虐" in source:
        add("tear")
    if "搞笑" in source or "好笑" in source:
        add("laugh")
    if not picked:
        add("question", "charge")
    return picked[:4]


def fallback_upgrade(context: dict[str, Any], reason: str) -> dict[str, Any]:
    updates = []
    slots = []
    for item in context["existing_highlights"]:
        updates.append(normalize_update({}, item))
        text = f"{item.get('title', '')} {item.get('description', '')} {item.get('highlight_type', '')} {item.get('emotion', '')}"
        start = float(item["start_time_sec"])
        end = float(item["end_time_sec"])
        slots.append(
            {
                "start_time_sec": round(start, 2),
                "end_time_sec": round(max(end, start + 4), 2),
                "asset_ids": fallback_asset_ids(text, context["drama_title"]),
                "cadence_sec": 3,
                "burst_count": 2,
                "meaning": f"本地兜底：围绕《{item.get('title', '高光')}》配置情绪贴图，需人工复核。",
                "asset_prompt": "",
            }
        )
    return {
        "source": "local_fallback",
        "fallback_reason": reason,
        "highlight_updates": updates,
        "sticker_timeline": slots,
        "draft_notes": ["模型调用失败或输出不可用，本集使用保守兜底升级。"],
    }


def normalize_slots(raw_slots: Any, context: dict[str, Any]) -> list[dict[str, Any]]:
    slots = []
    if not isinstance(raw_slots, list):
        raw_slots = []
    for slot in raw_slots:
        if not isinstance(slot, dict):
            continue
        try:
            start = float(slot.get("start_time_sec", slot.get("start", 0)))
            end = float(slot.get("end_time_sec", slot.get("end", start + 6)))
        except (TypeError, ValueError):
            continue
        asset_ids = [str(item) for item in slot.get("asset_ids", slot.get("assets", [])) if str(item) in ASSET_IDS]
        if not asset_ids:
            text = str(slot.get("meaning", ""))
            asset_ids = fallback_asset_ids(text, context["drama_title"])
        slots.append(
            {
                "start_time_sec": round(max(0, start), 2),
                "end_time_sec": round(max(start + 1, end), 2),
                "asset_ids": asset_ids[:5],
                "cadence_sec": max(1, int(slot.get("cadence_sec", slot.get("cadence", 3)) or 3)),
                "burst_count": max(1, int(slot.get("burst_count", slot.get("burst", 2)) or 2)),
                "meaning": str(slot.get("meaning") or "大模型生成的特色贴图时间窗，需人工复核。")[:500],
                "asset_prompt": str(slot.get("asset_prompt") or "")[:500],
            }
        )
    if slots:
        return sorted(slots, key=lambda item: item["start_time_sec"])
    return fallback_upgrade(context, "empty_slots")["sticker_timeline"]


def normalize_payload(raw: dict[str, Any], context: dict[str, Any]) -> dict[str, Any]:
    existing_by_id = {int(item["id"]): item for item in context["existing_highlights"]}
    raw_updates = raw.get("highlight_updates") if isinstance(raw.get("highlight_updates"), list) else []
    raw_by_id = {}
    for item in raw_updates:
        if isinstance(item, dict):
            try:
                raw_by_id[int(item.get("id"))] = item
            except (TypeError, ValueError):
                continue
    updates = [normalize_update(raw_by_id.get(highlight_id, {}), existing) for highlight_id, existing in existing_by_id.items()]
    return {
        "source": "llm_seed",
        "model_version": MODEL_VERSION,
        "episode_id": context["episode_id"],
        "drama_title": context["drama_title"],
        "episode_title": context["episode_title"],
        "has_rich_segments": context["has_rich_segments"],
        "highlight_updates": updates,
        "sticker_timeline": normalize_slots(raw.get("sticker_timeline"), context),
        "draft_notes": raw.get("draft_notes") if isinstance(raw.get("draft_notes"), list) else [],
    }


def build_upgrade(context: dict[str, Any], require_llm: bool) -> dict[str, Any]:
    try:
        raw = call_llm(build_prompt(context))
        return normalize_payload(raw, context)
    except Exception as exc:
        if require_llm:
            raise
        return {
            "episode_id": context["episode_id"],
            "drama_title": context["drama_title"],
            "episode_title": context["episode_title"],
            **fallback_upgrade(context, exc.__class__.__name__),
            "model_version": MODEL_VERSION,
        }


def default_theme(context: dict[str, Any]) -> dict[str, Any]:
    title = context["drama_title"]
    if "北往" in title:
        return {"theme_key": "road", "name": "北往返乡烟火", "class_name": "theme-road", "badge": "返乡烟火", "signal": "讨薪 / 回家 / 摩托", "accent": "#ff7a30", "soft": "#f2e2c4", "endpoint_label": "家"}
    if "冬至" in title:
        return {"theme_key": "winter", "name": "冬至心动夜", "class_name": "theme-winter", "badge": "冬日情绪", "signal": "雪夜 / 心动 / 选择", "accent": "#9fd8ff", "soft": "#ff9bbd", "endpoint_label": "心"}
    if "云渺" in title or "修仙" in title:
        return {"theme_key": "xianxia", "name": "云渺灵雨", "class_name": "theme-xianxia", "badge": "灵雨悬念", "signal": "雨伞 / 老宅 / 神秘身份", "accent": "#8bd3ff", "soft": "#e7c6ff", "endpoint_label": "灵"}
    if "寻宝" in title:
        return {"theme_key": "treasure", "name": "北派机关线索", "class_name": "theme-treasure", "badge": "寻宝线索", "signal": "罗盘 / 地图 / 机关", "accent": "#d6b45f", "soft": "#55d68f", "endpoint_label": "宝"}
    return {"theme_key": "special", "name": f"{title}专属互动", "class_name": "theme-city", "badge": "AI 高光体验", "signal": "高光 / 弹幕 / 贴图", "accent": "#ff7a30", "soft": "#12d6b0", "endpoint_label": "AI"}


def default_danmaku_modes() -> dict[str, Any]:
    return {
        "light": {"label": "轻聊", "enabled": True, "density": 0.72, "include_modes": ["light"]},
        "carnival": {"label": "狂欢", "enabled": True, "density": 1, "include_modes": ["light", "curated", "seed", "carnival"]},
        "immerse": {"label": "沉浸", "enabled": False, "density": 0, "include_modes": []},
    }


def upsert_database(payload: dict[str, Any]) -> None:
    with SessionLocal() as db:
        episode = db.get(Episode, int(payload["episode_id"]))
        if not episode:
            raise SystemExit(f"episode not found: {payload['episode_id']}")
        highlights = {item.id: item for item in db.query(Highlight).filter(Highlight.episode_id == episode.id).all()}
        for update in payload["highlight_updates"]:
            row = highlights.get(int(update["id"]))
            if not row:
                continue
            row.title = update["title"]
            row.description = update["description"]
            row.highlight_type = update["highlight_type"]
            row.emotion = update["emotion"]
            row.options_json = json.dumps(update["options"], ensure_ascii=False)
            row.annotation_reason = update["annotation_reason"]
            row.evidence_text = update["evidence_text"]
            row.model_version = MODEL_VERSION

        config_row = db.query(EpisodeExperienceConfig).filter(EpisodeExperienceConfig.episode_id == episode.id).first()
        if config_row:
            config = read_json_field(config_row.config_json, {})
        else:
            config = {
                "player_theme": default_theme(payload),
                "danmaku_modes": default_danmaku_modes(),
                "draft_notes": [],
            }
            config_row = EpisodeExperienceConfig(episode_id=episode.id)
            db.add(config_row)
        config.setdefault("player_theme", default_theme(payload))
        config.setdefault("danmaku_modes", default_danmaku_modes())
        notes = list(config.get("draft_notes") or [])
        notes.append("已由大模型批量升级高光表达和贴图时间窗，需在复核页抽样确认。")
        config["draft_notes"] = notes[-6:]
        config["sticker_timeline"] = payload["sticker_timeline"]
        config_row.version = int(config_row.version or 1) + 1
        config_row.source = payload.get("source", "llm_seed")
        config_row.model_version = MODEL_VERSION
        config_row.review_status = "llm_draft"
        config_row.config_json = json.dumps(config, ensure_ascii=False)
        db.commit()


def episode_ids_from_db() -> list[int]:
    with SessionLocal() as db:
        return [row[0] for row in db.query(Episode.id).order_by(Episode.id.asc()).all()]


def main() -> None:
    parser = argparse.ArgumentParser(description="Upgrade existing highlight copy and sticker timelines with an LLM.")
    parser.add_argument("--episode-id", type=int, action="append", default=[], help="Episode id. Repeatable.")
    parser.add_argument("--all", action="store_true", help="Process every episode in the database.")
    parser.add_argument("--write-db", action="store_true", help="Write upgrades back to app.db.")
    parser.add_argument("--require-llm", action="store_true", help="Fail instead of using local fallback when LLM fails.")
    args = parser.parse_args()

    episode_ids = episode_ids_from_db() if args.all else args.episode_id
    if not episode_ids:
        raise SystemExit("Pass --all or at least one --episode-id.")

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    summary = []
    for episode_id in episode_ids:
        context = load_context(episode_id)
        payload = build_upgrade(context, args.require_llm)
        output_path = OUTPUT_DIR / f"episode_{episode_id}_highlight_sticker_upgrade.json"
        write_json(output_path, payload)
        if args.write_db:
            upsert_database(payload)
        row = {
            "episode_id": episode_id,
            "drama_title": context["drama_title"],
            "source": payload.get("source"),
            "highlights": len(payload.get("highlight_updates", [])),
            "sticker_slots": len(payload.get("sticker_timeline", [])),
            "rich_context": context["has_rich_segments"],
            "output": str(output_path),
        }
        summary.append(row)
        print(json.dumps(row, ensure_ascii=False))

    write_json(OUTPUT_DIR / "summary.json", {"model_version": MODEL_VERSION, "episodes": summary})


if __name__ == "__main__":
    main()
