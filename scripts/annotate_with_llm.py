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

from backend.app.annotation_schema import ALLOWED_EMOTIONS, ALLOWED_HIGHLIGHT_TYPES, validate_annotation_payload
from backend.app.config import ROOT_DIR


load_dotenv(ROOT_DIR / ".env")


PROMPT_VERSION = "highlight-annotation-v2"


SYSTEM_PROMPT = f"""
你是短剧互动产品的剧情高光标注专家。
你的任务是根据分段字幕、画面描述、音频提示和人工备注，找出适合触发低打扰互动组件的剧情高光点。

允许的高光类型：{", ".join(sorted(ALLOWED_HIGHLIGHT_TYPES))}
允许的情绪标签：{", ".join(sorted(ALLOWED_EMOTIONS))}

输出要求：
1. 只输出一个 JSON 对象，不要 Markdown，不要解释。
2. 每集建议输出 3-5 个高光点；短集不能处处都是高光，普通铺垫和过场不要强行标。
3. 两个高光点之间尽量间隔 25 秒以上，除非连续剧情确实发生强反转。
4. 高光点必须落在输入片段时间范围内。
5. 互动按钮必须短、直接、有情绪感染力，每个 label 1-8 个字。
6. 只能依据 segments 里的字幕、画面描述、音频提示和人工备注标注。
7. 剧名、文件名、题材名都不是剧情证据，不能据此推断剧情。
8. 每个高光点必须给出 evidence_segment_ids 和 evidence_text，证明确实来自输入片段。
9. manual_note 是人工复核者给出的可靠剧情观察，可以作为证据。
10. 如果 manual_note 中写有“候选高光”，请优先判断它是否适合转成互动高光点。
11. 如果信息不足，可以少标，不要硬编剧情。
12. 每个高光点必须输出 2-4 个互动按钮 options；如果两个候选时间太近，请合并成一个更强高光。
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


def slugify_option(label: str, index: int) -> str:
    ascii_key = re.sub(r"[^a-zA-Z0-9]+", "_", label).strip("_").lower()
    return ascii_key or f"option_{index + 1}"


def default_option_labels(emotion: str) -> list[str]:
    mapping = {
        "爽": ["爽到了", "再来"],
        "解气": ["解气", "再来"],
        "燃": ["燃起来", "再来"],
        "震惊": ["震惊", "还能这样"],
        "意外": ["没想到", "再看"],
        "恍然大悟": ["懂了", "再看"],
        "心疼": ["心疼", "抱抱"],
        "难过": ["破防了", "抱抱"],
        "破防": ["破防了", "抱抱"],
        "愤怒": ["气到了", "站他"],
        "站队": ["站他", "站她"],
        "好笑": ["笑死", "鹅鹅鹅"],
        "离谱": ["离谱", "再看"],
        "欢乐": ["哈哈哈", "再来"],
        "心动": ["心动", "磕到了"],
        "磕到了": ["磕到了", "甜晕"],
        "甜": ["好甜", "磕到了"],
        "紧张": ["紧张", "屏住"],
        "担心": ["担心", "别出事"],
        "屏息": ["屏住", "别停"],
        "期待": ["期待", "下一集"],
        "好奇": ["想知道", "下一集"],
    }
    return mapping.get(emotion, [emotion[:8] or "表达", "再看"])


def normalize_options(item: dict[str, Any]) -> None:
    options = item.get("options")
    if options is None:
        for alias in ("interaction_options", "buttons", "button_texts", "labels"):
            if alias in item:
                options = item[alias]
                break

    if isinstance(options, dict):
        options = [{"key": str(key), "label": str(value)} for key, value in options.items()]
    elif isinstance(options, list):
        normalized = []
        for index, option in enumerate(options):
            if isinstance(option, dict):
                key = option.get("key") or option.get("id") or slugify_option(str(option.get("label", "")), index)
                label = str(option.get("label") or option.get("text") or option.get("name") or key).strip()[:8]
                normalized.append({"key": str(key), "label": label})
            else:
                label = str(option).strip()[:8]
                normalized.append({"key": slugify_option(label, index), "label": label})
        options = normalized

    if not isinstance(options, list) or not options:
        emotion = item.get("emotion", "表达")
        options = [
            {"key": "react", "label": str(emotion)[:8]},
            {"key": "again", "label": "再看一遍"},
        ]

    seen_keys = set()
    deduped = []
    for index, option in enumerate(options):
        key = str(option.get("key") or slugify_option(str(option.get("label", "")), index)).strip()
        label = str(option.get("label") or key).strip()[:8]
        if not key or key in seen_keys or not label:
            continue
        seen_keys.add(key)
        deduped.append({"key": key, "label": label})

    for label in default_option_labels(str(item.get("emotion", ""))):
        if len(deduped) >= 2:
            break
        key = slugify_option(label, len(deduped))
        if key not in seen_keys:
            seen_keys.add(key)
            deduped.append({"key": key, "label": label[:8]})

    item["options"] = deduped[:4]


def normalize_emotion(item: dict[str, Any]) -> None:
    emotion = str(item.get("emotion", "")).strip()
    alias_map = {
        "感动": "心疼",
        "想家": "心疼",
        "共情": "心疼",
        "委屈": "心疼",
        "伤感": "难过",
        "心酸": "破防",
        "温暖": "心动",
        "温情": "心动",
        "开心": "欢乐",
        "爆笑": "好笑",
        "惊讶": "震惊",
        "惊喜": "意外",
        "反转": "震惊",
        "悬念": "期待",
        "爽点": "爽",
    }
    if emotion in ALLOWED_EMOTIONS:
        return
    if emotion in alias_map:
        item["emotion"] = alias_map[emotion]
        return

    fallback_by_type = {
        "冲突对抗": "站队",
        "反转揭秘": "震惊",
        "爽点逆袭": "爽",
        "甜蜜心动": "心动",
        "虐心共情": "心疼",
        "悬念钩子": "期待",
        "搞笑解压": "好笑",
        "危机紧张": "紧张",
    }
    item["emotion"] = fallback_by_type.get(str(item.get("highlight_type", "")), "期待")


def normalize_annotation_payload(payload: dict[str, Any]) -> dict[str, Any]:
    for item in payload.get("highlights", []):
        if isinstance(item, dict):
            normalize_emotion(item)
            normalize_options(item)
    return payload


def build_user_prompt(annotation_input: dict[str, Any]) -> str:
    episode_context = {
        "episode_id": annotation_input["episode_id"],
        "duration_sec": annotation_input.get("duration_sec"),
        "segments": annotation_input.get("segments", []),
    }
    return json.dumps(
        {
            "task": "请输出短剧高光点标注 JSON。",
            "important_rules": [
                "只能依据 episode.segments 中的内容标注。",
                "不要使用剧名、文件名或题材名推断剧情。",
                "如果没有明确证据，不要标注该高光点。",
                "保持高光稀疏：短剧一集通常只保留 3-5 个最强情绪峰值。",
            ],
            "schema": {
                "episode_id": "number",
                "prompt_version": PROMPT_VERSION,
                "highlights": [
                    {
                        "start_time_sec": "number",
                        "end_time_sec": "number",
                        "title": "string",
                        "description": "string",
                        "highlight_type": "冲突对抗/反转揭秘/爽点逆袭/甜蜜心动/虐心共情/悬念钩子/搞笑解压/危机紧张",
                        "emotion": "爽/震惊/心疼/愤怒/好笑/心动/紧张/期待/站队/解气/破防等",
                        "confidence": "0-1 number",
                        "reason": "string",
                        "evidence_segment_ids": ["number"],
                        "evidence_text": "来自输入片段的字幕/画面/备注证据",
                        "options": [{"key": "string", "label": "string"}],
                    }
                ],
            },
            "episode": episode_context,
        },
        ensure_ascii=False,
    )


def build_retry_prompt(annotation_input: dict[str, Any], errors: list[str], previous_payload: dict[str, Any]) -> str:
    return json.dumps(
        {
            "task": "上一次输出未通过校验，请根据错误修正并重新输出完整 JSON。",
            "validation_errors": errors,
            "fix_rules": [
                "每个 highpoint 的 options 必须是 2-4 个按钮。",
                "相邻高光 start_time_sec 必须至少间隔 25 秒；太近时保留更强的一个或合并。",
                "仍然只能依据 episode.segments，不要补编新剧情。",
                "请输出完整 JSON 对象，不要只输出差异。",
            ],
            "previous_payload": previous_payload,
            "episode": {
                "episode_id": annotation_input["episode_id"],
                "duration_sec": annotation_input.get("duration_sec"),
                "segments": annotation_input.get("segments", []),
            },
        },
        ensure_ascii=False,
    )


def has_episode_context(annotation_input: dict[str, Any]) -> bool:
    context_fields = ("subtitle_text", "visual_note", "audio_note", "manual_note")
    for segment in annotation_input.get("segments", []):
        if any(str(segment.get(field, "")).strip() for field in context_fields):
            return True
    return False


def call_chat_completion(prompt: str) -> dict[str, Any]:
    api_key = os.getenv("ARK_API_KEY")
    model = os.getenv("ARK_MODEL") or os.getenv("ARK_ENDPOINT_ID")
    base_url = os.getenv("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/")
    if not api_key or not model:
        raise RuntimeError("请在 .env 中配置 ARK_API_KEY 和 ARK_MODEL 或 ARK_ENDPOINT_ID。")

    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.2,
    }
    request = urllib.request.Request(
        f"{base_url}/chat/completions",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"大模型请求失败：HTTP {exc.code} {detail}") from exc

    content = data["choices"][0]["message"]["content"]
    return extract_json(content)


def dry_run_annotation(annotation_input: dict[str, Any]) -> dict[str, Any]:
    duration = annotation_input.get("duration_sec") or 180
    return {
        "episode_id": annotation_input["episode_id"],
        "prompt_version": PROMPT_VERSION,
        "highlights": [
            {
                "start_time_sec": round(duration * 0.25, 2),
                "end_time_sec": round(duration * 0.25 + 8, 2),
                "title": "演示高光候选",
                "description": "dry-run 生成的占位标注，用于验证链路。",
                "highlight_type": "爽点逆袭",
                "emotion": "爽",
                "confidence": 0.5,
                "reason": "未调用大模型，仅用于测试 JSON 格式和入库链路。",
                "evidence_segment_ids": [1],
                "evidence_text": "dry-run 占位证据。",
                "options": [
                    {"key": "shuang", "label": "爽"},
                    {"key": "again", "label": "再来"},
                    {"key": "support", "label": "站她"},
                ],
            }
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="调用大模型生成短剧高光标注 JSON。")
    parser.add_argument("--input", type=Path, required=True, help="prepare_annotation_input.py 生成的输入 JSON。")
    parser.add_argument("--output", type=Path, default=None, help="输出标注 JSON。")
    parser.add_argument("--dry-run", action="store_true", help="不调用大模型，只生成占位标注验证链路。")
    parser.add_argument("--allow-empty-context", action="store_true", help="允许在没有字幕/画面备注时调用模型，仅用于连通性测试。")
    args = parser.parse_args()

    annotation_input = json.loads(args.input.read_text(encoding="utf-8"))
    if args.dry_run:
        payload = dry_run_annotation(annotation_input)
    else:
        if not args.allow_empty_context and not has_episode_context(annotation_input):
            raise SystemExit(
                "当前输入没有字幕、画面描述、音频提示或人工备注。"
                "为避免模型根据剧名脑补剧情，请先补充 context，或仅在连通性测试时添加 --allow-empty-context。"
            )
        payload = call_chat_completion(build_user_prompt(annotation_input))
        payload.setdefault("episode_id", annotation_input["episode_id"])
        payload.setdefault("prompt_version", PROMPT_VERSION)
    payload = normalize_annotation_payload(payload)

    errors = validate_annotation_payload(payload)
    if errors and not args.dry_run:
        payload = call_chat_completion(build_retry_prompt(annotation_input, errors, payload))
        payload.setdefault("episode_id", annotation_input["episode_id"])
        payload.setdefault("prompt_version", PROMPT_VERSION)
        payload = normalize_annotation_payload(payload)
        errors = validate_annotation_payload(payload)
    if errors:
        raise SystemExit("标注 JSON 校验失败：\n" + "\n".join(f"- {error}" for error in errors))

    output = args.output or ROOT_DIR / "data" / "annotations" / f"episode_{annotation_input['episode_id']}_llm.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
