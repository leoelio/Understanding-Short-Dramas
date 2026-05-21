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


PROMPT_VERSION = "highlight-annotation-v1"


SYSTEM_PROMPT = f"""
你是短剧互动产品的剧情高光标注专家。
你的任务是根据分段字幕、画面描述、音频提示和人工备注，找出适合触发低打扰互动组件的剧情高光点。

允许的高光类型：{", ".join(sorted(ALLOWED_HIGHLIGHT_TYPES))}
允许的情绪标签：{", ".join(sorted(ALLOWED_EMOTIONS))}

输出要求：
1. 只输出一个 JSON 对象，不要 Markdown，不要解释。
2. 每集建议输出 3-5 个高光点。
3. 高光点必须落在输入片段时间范围内。
4. 互动按钮必须短、直接、有情绪感染力，每个 label 1-8 个字。
5. 如果信息不足，可以少标，不要硬编剧情。
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


def build_user_prompt(annotation_input: dict[str, Any]) -> str:
    return json.dumps(
        {
            "task": "请输出短剧高光点标注 JSON。",
            "schema": {
                "episode_id": "number",
                "prompt_version": PROMPT_VERSION,
                "highlights": [
                    {
                        "start_time_sec": "number",
                        "end_time_sec": "number",
                        "title": "string",
                        "description": "string",
                        "highlight_type": "冲突/反转/爽点/甜蜜/虐点/搞笑/悬念/名场面",
                        "emotion": "爽/震惊/心疼/愤怒/好笑/心动/紧张/期待",
                        "confidence": "0-1 number",
                        "reason": "string",
                        "options": [{"key": "string", "label": "string"}],
                    }
                ],
            },
            "episode": annotation_input,
        },
        ensure_ascii=False,
    )


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
                "highlight_type": "爽点",
                "emotion": "爽",
                "confidence": 0.5,
                "reason": "未调用大模型，仅用于测试 JSON 格式和入库链路。",
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
    args = parser.parse_args()

    annotation_input = json.loads(args.input.read_text(encoding="utf-8"))
    if args.dry_run:
        payload = dry_run_annotation(annotation_input)
    else:
        payload = call_chat_completion(build_user_prompt(annotation_input))
        payload.setdefault("episode_id", annotation_input["episode_id"])
        payload.setdefault("prompt_version", PROMPT_VERSION)

    errors = validate_annotation_payload(payload)
    if errors:
        raise SystemExit("标注 JSON 校验失败：\n" + "\n".join(f"- {error}" for error in errors))

    output = args.output or ROOT_DIR / "data" / "annotations" / f"episode_{annotation_input['episode_id']}_llm.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
