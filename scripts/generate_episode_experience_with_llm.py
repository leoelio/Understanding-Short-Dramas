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


load_dotenv(ROOT_DIR / ".env")


SYSTEM_PROMPT = """
你是短剧互动体验策略师，负责把短剧字幕、画面备注和人工高光转成可落地的移动端互动策略。

必须只输出 JSON 对象，不要 Markdown，不要解释。
要求：
1. 贴图热词必须来自字幕/画面/高光依据，不要泛泛而谈。
2. 每个贴图必须有明确时间窗口，说明为什么出现在这里。
3. 不要让贴图在无关时间乱出现。非高光段只允许少量氛围贴图。
4. 弹幕要分三类：light=轻聊，carnival=狂欢，seed=沉浸模式仍用于数据预置但默认不展示。
5. 弹幕不得剧透后续剧情，不得提前说出摩托车揭晓。
6. 输出内容要适合前端直接复核和人工改写。
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


def load_context(input_path: Path, highlights_path: Path) -> dict[str, Any]:
    episode = json.loads(input_path.read_text(encoding="utf-8"))
    reviewed = json.loads(highlights_path.read_text(encoding="utf-8"))
    highlights: list[dict[str, Any]] = []
    for item in reviewed.get("episodes", []):
        if item.get("drama_title") == episode.get("drama_title") and int(item.get("episode_no", 0)) == 1:
            highlights = item.get("highlights", [])
            break
    return {
        "drama_title": episode.get("drama_title"),
        "episode_title": episode.get("episode_title"),
        "duration_sec": episode.get("duration_sec"),
        "segments": episode.get("segments", []),
        "reviewed_highlights": highlights,
    }


def build_prompt(context: dict[str, Any]) -> str:
    return json.dumps(
        {
            "task": "生成贴图时间轴、热词、播放器元素和三档弹幕策略。",
            "output_schema": {
                "hot_terms": [
                    {
                        "term": "来自字幕或画面的热词",
                        "start_time_sec": 0,
                        "end_time_sec": 0,
                        "meaning": "为什么值得互动",
                        "asset_id": "建议贴图标识",
                    }
                ],
                "sticker_timeline": [
                    {
                        "start_time_sec": 0,
                        "end_time_sec": 0,
                        "slot_name": "时间段名称",
                        "asset_ids": ["asset id"],
                        "meaning": "出现原因",
                        "placement": "建议位置，避免遮挡中心人物",
                        "click_effect": "点击反馈建议",
                    }
                ],
                "player_theme_elements": [
                    {
                        "element": "播放器元素",
                        "visual": "视觉设计",
                        "reason": "和剧情的关系",
                    }
                ],
                "danmaku_comments": [
                    {
                        "time_sec": 0,
                        "text": "弹幕文案",
                        "mode": "light|carnival|seed",
                        "intent": "轻聊/狂欢/沉浸预置的差异说明",
                    }
                ],
            },
            "episode": context,
        },
        ensure_ascii=False,
    )


def fallback(context: dict[str, Any]) -> dict[str, Any]:
    return {
        "hot_terms": [
            {"term": "一分没结", "start_time_sec": 0, "end_time_sec": 20, "meaning": "欠薪处境", "asset_id": "noPayBill"},
            {"term": "走", "start_time_sec": 12, "end_time_sec": 24, "meaning": "工友准备讨薪", "asset_id": "goSign"},
            {"term": "钱凑够", "start_time_sec": 40, "end_time_sec": 62, "meaning": "讨薪释放", "asset_id": "debtCash"},
            {"term": "年三十到家", "start_time_sec": 140, "end_time_sec": 180, "meaning": "亲情承诺", "asset_id": "homeLantern"},
            {"term": "安安全全", "start_time_sec": 160, "end_time_sec": 180, "meaning": "父母叮嘱", "asset_id": "homePhone"},
            {"term": "咋不想啊", "start_time_sec": 180, "end_time_sec": 210, "meaning": "回家悬念", "asset_id": "smokeQuestion"},
            {"term": "摇滚", "start_time_sec": 240, "end_time_sec": 282, "meaning": "交通工具揭晓铺垫", "asset_id": "rockWord"},
        ],
        "sticker_timeline": [
            {"start_time_sec": 10, "end_time_sec": 62, "slot_name": "讨薪对峙", "asset_ids": ["noPayBill", "goSign", "debtCash", "wageStamp"], "meaning": "欠薪、行动、结清", "placement": "左右边缘，避开中心人物", "click_effect": "盖章、现金粒子"},
            {"start_time_sec": 112, "end_time_sec": 180, "slot_name": "想家电话", "asset_ids": ["homePhone", "homeLantern", "homeTicket"], "meaning": "电话、年三十、安安全全", "placement": "下方左右角", "click_effect": "暖光扩散"},
            {"start_time_sec": 198, "end_time_sec": 244, "slot_name": "回家悬念", "asset_ids": ["smokeQuestion", "roadQuestion"], "meaning": "抽烟、路、能否回去", "placement": "右侧上半区", "click_effect": "烟雾和问号粒子"},
            {"start_time_sec": 244, "end_time_sec": 300, "slot_name": "摇滚返乡", "asset_ids": ["rockWord", "rockMoto"], "meaning": "摇滚台词、行李摩托", "placement": "边缘滑入", "click_effect": "尾气火焰"},
        ],
        "player_theme_elements": [
            {"element": "顶部标题带", "visual": "北往路牌和旧车票齿孔", "reason": "强化返乡路线"},
            {"element": "进度条", "visual": "路灯光带和摩托进度点", "reason": "表达回家路"},
            {"element": "弹幕", "visual": "暖橙描边，狂欢模式更亮", "reason": "贴合烟火气"},
        ],
        "danmaku_comments": [],
        "source": "local_fallback",
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
        "temperature": 0.55,
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


def main() -> None:
    parser = argparse.ArgumentParser(description="生成剧集互动体验策略。")
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--highlights", type=Path, default=ROOT / "backend/app/fixtures/reviewed_highlights.json")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    context = load_context(args.input, args.highlights)
    try:
        payload = call_llm(build_prompt(context))
        payload["source"] = "llm"
    except Exception as exc:
        payload = fallback(context)
        payload["fallback_reason"] = exc.__class__.__name__

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
