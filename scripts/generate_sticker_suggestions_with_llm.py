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
from backend.app.models import Episode, Highlight


load_dotenv(ROOT_DIR / ".env")


ASSET_CATALOG = [
    {"id": "mealSteam", "label": "打工人日常", "group": "北往返乡"},
    {"id": "noPayBill", "label": "一分没结", "group": "北往返乡"},
    {"id": "goSign", "label": "走", "group": "北往返乡"},
    {"id": "debtCash", "label": "欠薪结清", "group": "北往返乡"},
    {"id": "homePhone", "label": "想家了", "group": "北往返乡"},
    {"id": "homeLantern", "label": "安安全全", "group": "北往返乡"},
    {"id": "smokeQuestion", "label": "悬着心", "group": "北往返乡"},
    {"id": "wageStamp", "label": "欠薪得还", "group": "北往返乡"},
    {"id": "homeTicket", "label": "年三十到家", "group": "北往返乡"},
    {"id": "roadQuestion", "label": "回得去吗", "group": "北往返乡"},
    {"id": "rockWord", "label": "你这叫摇滚啊", "group": "北往返乡"},
    {"id": "rockMoto", "label": "贼摇滚摩托", "group": "北往返乡"},
    {"id": "northTitle", "label": "北往", "group": "北往返乡"},
    {"id": "xianxiaRain", "label": "灵雨伞", "group": "仙侠悬念"},
    {"id": "xianxiaSeal", "label": "法阵亮起", "group": "仙侠悬念"},
    {"id": "xianxiaSpirit", "label": "灵气现形", "group": "仙侠悬念"},
    {"id": "treasureMap", "label": "藏宝图", "group": "寻宝机关"},
    {"id": "treasureCompass", "label": "罗盘指针", "group": "寻宝机关"},
    {"id": "treasureTrap", "label": "机关警报", "group": "寻宝机关"},
    {"id": "winterSnow", "label": "冬至雪", "group": "冬至爱情"},
    {"id": "winterHeart", "label": "心事", "group": "冬至爱情"},
    {"id": "winterMemory", "label": "旧照片", "group": "冬至爱情"},
    {"id": "winterCrow", "label": "乌鸦无语", "group": "冬至爱情"},
    {"id": "winterWow", "label": "哇塞", "group": "冬至爱情"},
    {"id": "winterKiss", "label": "突然亲吻", "group": "冬至爱情"},
    {"id": "winterChoice", "label": "选哪边", "group": "冬至爱情"},
    {"id": "winterBrokenHeart", "label": "心碎", "group": "冬至爱情"},
    {"id": "winterBlush", "label": "脸红", "group": "冬至爱情"},
    {"id": "winterHeartbeat", "label": "心跳", "group": "冬至爱情"},
    {"id": "winterHoldBack", "label": "别冲动", "group": "冬至爱情"},
    {"id": "winterQuestionLove", "label": "爱还是现实", "group": "冬至爱情"},
    {"id": "winterWarmHug", "label": "抱抱", "group": "冬至爱情"},
    {"id": "vehicleTrain", "label": "火车", "group": "交通选择"},
    {"id": "vehicleCar", "label": "小车", "group": "交通选择"},
    {"id": "vehicleMotorcycle", "label": "摩托车", "group": "交通选择"},
    {"id": "charge", "label": "冲", "group": "通用情绪"},
    {"id": "question", "label": "问号", "group": "通用情绪"},
    {"id": "laugh", "label": "好笑", "group": "通用情绪"},
    {"id": "rock", "label": "摇滚", "group": "通用情绪"},
    {"id": "tear", "label": "心疼", "group": "通用情绪"},
]

ALLOWED_ASSET_IDS = {item["id"] for item in ASSET_CATALOG}

SYSTEM_PROMPT = """
你是短剧互动贴图策略师。你只为人工复核台生成贴图时间窗建议 JSON。

必须只输出 JSON 对象，不要 Markdown，不要解释。
必须遵守：
1. asset_ids 只能从 asset_catalog 的 id 中选择。
2. 贴图时间窗必须贴合高光点和字幕/备注，不要乱放，不要过密。
3. 每个高光最多推荐 2-4 个贴图；爱情甜点可以 4 个，其他一般 2-3 个。
4. 每个时间窗必须写 meaning，说明它为什么对应当前剧情。
5. 不要输出 API Key、接入点、环境变量或任何密钥内容。
""".strip()


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
    return {
        "start_time_sec": highlight.start_time_sec,
        "end_time_sec": highlight.end_time_sec,
        "title": highlight.title,
        "description": highlight.description,
        "highlight_type": highlight.highlight_type,
        "emotion": highlight.emotion,
        "annotation_reason": highlight.annotation_reason,
        "evidence_text": highlight.evidence_text,
    }


def load_context(episode_id: int) -> dict[str, Any]:
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
            "duration_sec": episode.duration_sec,
            "highlights": [highlight_payload(item) for item in highlights],
            "segments": [],
        }

    input_path = annotation_input_path(episode_id)
    if input_path.exists():
        payload = json.loads(input_path.read_text(encoding="utf-8"))
        context["segments"] = [
            {
                "start_time_sec": item.get("start_time_sec"),
                "end_time_sec": item.get("end_time_sec"),
                "subtitle_text": item.get("subtitle_text", ""),
                "visual_note": item.get("visual_note", ""),
                "manual_note": item.get("manual_note", ""),
            }
            for item in payload.get("segments", [])[:80]
        ]
    return context


def build_prompt(context: dict[str, Any]) -> str:
    return json.dumps(
        {
            "task": "生成可导入复核页的贴图建议 JSON。",
            "output_schema": {
                "source": "llm_sticker_suggestion",
                "model_version": "sticker-suggestion-v1",
                "slots": [
                    {
                        "start_time_sec": 0,
                        "end_time_sec": 8,
                        "asset_ids": ["只能使用 asset_catalog 中的 id"],
                        "cadence_sec": 2,
                        "burst_count": 3,
                        "meaning": "为什么这些贴图应该在这个剧情时间窗出现",
                        "asset_prompt": "如果后续用图像模型重绘贴图，可使用的中文提示词",
                    }
                ],
            },
            "asset_catalog": ASSET_CATALOG,
            "episode": context,
        },
        ensure_ascii=False,
    )


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
        "temperature": 0.45,
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


def pick_fallback_assets(text: str, highlight_type: str) -> list[str]:
    picks: list[str] = []

    def add(*asset_ids: str) -> None:
        for asset_id in asset_ids:
            if asset_id in ALLOWED_ASSET_IDS and asset_id not in picks:
                picks.append(asset_id)

    if any(word in text for word in ("亲", "心动", "撒糖", "磕")):
        add("winterKiss", "winterHeart", "winterBlush", "winterHeartbeat")
    if any(word in text for word in ("安乐死", "心疼", "伤心", "难过", "破防")):
        add("winterBrokenHeart", "winterWarmHug", "tear")
    if any(word in text for word in ("脱", "震惊", "突然")) and "亲" not in text:
        add("winterWow", "winterHoldBack", "question")
    if any(word in text for word in ("选择", "选哪", "现实", "回不去")):
        add("winterChoice", "winterQuestionLove", "question")
    if any(word in text for word in ("欠薪", "要债", "工友")):
        add("wageStamp", "debtCash", "charge")
    if any(word in text for word in ("回家", "年三十", "摩托", "摇滚")):
        add("homeTicket", "roadQuestion", "rockMoto")
    if any(word in text for word in ("无语", "尴尬")):
        add("winterCrow")
    if not picks and "悬念" in highlight_type:
        add("question")
    if not picks:
        add("question", "tear")
    return picks[:4]


def fallback_payload(context: dict[str, Any]) -> dict[str, Any]:
    slots = []
    for highlight in context.get("highlights", []):
        start = float(highlight.get("start_time_sec") or 0)
        end = float(highlight.get("end_time_sec") or start + 8)
        text = " ".join(
            str(highlight.get(key, ""))
            for key in ("title", "description", "highlight_type", "emotion", "annotation_reason", "evidence_text")
        )
        assets = pick_fallback_assets(text, str(highlight.get("highlight_type", "")))
        slots.append(
            {
                "start_time_sec": round(start, 2),
                "end_time_sec": round(max(end, start + 4), 2),
                "asset_ids": assets,
                "cadence_sec": 1 if "甜" in text or "心动" in text else 2,
                "burst_count": 4 if "甜" in text or "心动" in text else 3,
                "meaning": f"基于高光《{highlight.get('title', '未命名')}》生成的本地兜底贴图建议，需人工复核。",
            }
        )
    return {
        "source": "local_fallback",
        "model_version": "sticker-suggestion-v1",
        "slots": slots,
    }


def normalize_payload(raw: dict[str, Any], context: dict[str, Any]) -> dict[str, Any]:
    raw_slots = raw.get("slots") or raw.get("suggestions") or raw.get("sticker_timeline") or []
    slots = []
    for slot in raw_slots:
        start = float(slot.get("start_time_sec", slot.get("start", 0)))
        end = float(slot.get("end_time_sec", slot.get("end", start + 8)))
        asset_ids = [item for item in slot.get("asset_ids", slot.get("assets", [])) if item in ALLOWED_ASSET_IDS]
        if not asset_ids:
            continue
        slots.append(
            {
                "start_time_sec": round(start, 2),
                "end_time_sec": round(max(end, start + 1), 2),
                "asset_ids": asset_ids[:5],
                "cadence_sec": max(1, int(slot.get("cadence_sec", slot.get("cadence", 2)))),
                "burst_count": max(1, int(slot.get("burst_count", slot.get("burst", 3)))),
                "meaning": str(slot.get("meaning") or slot.get("reason") or "大模型贴图建议，需人工复核。"),
                "asset_prompt": str(slot.get("asset_prompt", "")),
            }
        )
    if not slots:
        return fallback_payload(context)
    return {
        "source": raw.get("source", "llm_sticker_suggestion"),
        "model_version": raw.get("model_version", "sticker-suggestion-v1"),
        "slots": sorted(slots, key=lambda item: item["start_time_sec"]),
    }


def build_payload(context: dict[str, Any], require_llm: bool) -> dict[str, Any]:
    try:
        suggestion = normalize_payload(call_llm(build_prompt(context)), context)
    except Exception as exc:
        if require_llm:
            raise RuntimeError(exc.__class__.__name__) from exc
        suggestion = fallback_payload(context)
    return {
        "episode_id": context["episode_id"],
        "drama_title": context["drama_title"],
        "episode_title": context["episode_title"],
        **suggestion,
        "import_note": "在复核页点击“加载建议文件”或复制本 JSON 到导入框，再点击“导入并合并”。",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="生成复核页可导入的贴图建议 JSON。")
    parser.add_argument("--episode-id", type=int, required=True, help="数据库中的剧集 ID。")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=ROOT / "frontend" / "assets" / "sticker_suggestions",
        help="输出目录，默认供前端静态加载。",
    )
    parser.add_argument("--require-llm", action="store_true", help="大模型失败时直接失败，不使用本地兜底。")
    args = parser.parse_args()

    context = load_context(args.episode_id)
    payload = build_payload(context, args.require_llm)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output_path = args.output_dir / f"episode_{args.episode_id}_sticker_suggestions.json"
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"episode_id": args.episode_id, "source": payload["source"], "output": str(output_path)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
