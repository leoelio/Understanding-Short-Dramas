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
你是短剧互动产品的视觉体验设计专家和前端主题策略师。
你要根据短剧分段字幕、画面备注和已复核高光，输出适合移动端短剧播放器的主题与贴图策略。

要求：
1. 只输出 JSON 对象，不要 Markdown，不要解释。
2. 不要输出真实图片文件，只输出可被前端消费的主题、贴图、动效和触发规则。
3. 贴图必须和剧情内容强相关，避免泛泛的“冲”“哈哈”。
4. 播放器主题必须有记忆点，包含多元素：背景质感、进度条、播放键、音量键、弹幕样式、互动按钮、贴图风格。
5. 所有文案必须短，适合手机屏幕。
6. 不要引入版权角色、真实品牌或外部图片依赖。
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


def load_episode_context(input_path: Path, highlights_path: Path, episode_id: int) -> dict[str, Any]:
    annotation_input = json.loads(input_path.read_text(encoding="utf-8"))
    reviewed = json.loads(highlights_path.read_text(encoding="utf-8"))
    highlights: list[dict[str, Any]] = []
    for episode in reviewed.get("episodes", []):
        if episode.get("drama_title") == annotation_input.get("drama_title") and int(episode.get("episode_no", 0)) == int(
            annotation_input.get("episode_title", "第0集").strip("第集") or 0
        ):
            highlights = episode.get("highlights", [])
            break
    if not highlights:
        for episode in reviewed.get("episodes", []):
            if episode.get("drama_title") == annotation_input.get("drama_title"):
                highlights = episode.get("highlights", [])
                break

    return {
        "episode_id": episode_id,
        "drama_title": annotation_input.get("drama_title"),
        "episode_title": annotation_input.get("episode_title"),
        "duration_sec": annotation_input.get("duration_sec"),
        "segments": annotation_input.get("segments", []),
        "reviewed_highlights": highlights,
    }


def build_prompt(context: dict[str, Any]) -> str:
    return json.dumps(
        {
            "task": "为这个短剧生成个性化播放器主题和贴图互动策略。",
            "output_schema": {
                "drama_title": "string",
                "theme_id": "string",
                "theme_name": "string",
                "visual_positioning": "string",
                "palette": {"background": "hex", "accent": "hex", "soft": "hex", "text": "hex"},
                "player": {
                    "surface": "播放器背景质感",
                    "progress": "进度条设计",
                    "play_button": "播放键设计",
                    "volume_button": "声音键设计",
                    "ambient_elements": ["string"],
                },
                "stickers": [
                    {
                        "id": "string",
                        "label": "string",
                        "scene": "适配剧情场景",
                        "visual": "贴图视觉描述",
                        "trigger_keywords": ["string"],
                        "click_effect": "点击反馈",
                    }
                ],
                "rules": ["string"],
            },
            "episode": context,
        },
        ensure_ascii=False,
    )


def fallback_strategy(context: dict[str, Any]) -> dict[str, Any]:
    return {
        "drama_title": context.get("drama_title", "未知短剧"),
        "theme_id": "homeward_road",
        "theme_name": "返乡公路票根",
        "visual_positioning": "现实打工人返乡，朴素、暖光、路牌、车票、摩托和年味混在一起。",
        "palette": {"background": "#15100d", "accent": "#f6bc4f", "soft": "#12d6b0", "text": "#fff7e8"},
        "player": {
            "surface": "像一张压在视频下方的旧车票和公路路面，带微弱纸纹和虚线路标。",
            "progress": "公路黄线进度条，播放头像小摩托前灯。",
            "play_button": "路牌形播放键，暂停时变成两条车道线。",
            "volume_button": "喇叭/风声按钮，静音像路障。",
            "ambient_elements": ["票根齿孔", "公路虚线", "年三十小标签", "摩托尾灯"],
        },
        "stickers": [
            {
                "id": "wage_due_stamp",
                "label": "欠薪得还",
                "scene": "开头讨薪对峙",
                "visual": "红色讨薪印章拍在屏幕上，像工友把诉求盖章确认。",
                "trigger_keywords": ["欠薪", "讨薪", "要债", "工友"],
                "click_effect": "点击像盖章，次数多后印泥溅开。",
            },
            {
                "id": "home_ticket",
                "label": "年三十到家",
                "scene": "没钱也想回家过年",
                "visual": "暖色车票/家书贴纸，边缘有纸纹和小灯笼。",
                "trigger_keywords": ["回家", "过年", "父母", "年三十"],
                "click_effect": "点击冒出小票根和暖光。",
            },
            {
                "id": "road_question_sign",
                "label": "回得去吗",
                "scene": "能否回家悬念",
                "visual": "路口问号指示牌，带一点风吹晃动。",
                "trigger_keywords": ["到底", "能不能", "回不去", "猜"],
                "click_effect": "点击路牌抖动，问号变大。",
            },
            {
                "id": "rock_luggage_moto",
                "label": "贼摇滚",
                "scene": "摩托返乡揭晓",
                "visual": "绑着大包行李的摩托剪影，尾灯和音符抖动。",
                "trigger_keywords": ["摇滚", "摩托", "交通工具", "回家方式"],
                "click_effect": "点击尾灯变亮，5次后火焰尾气，10次后整张贴纸放大。",
            },
        ],
        "rules": [
            "普通播放中少量出现路牌/票根，狂欢模式加倍。",
            "高光点前后优先出现对应剧情贴图。",
            "贴图点击不要遮挡人物脸，默认在画面边缘弹出。",
        ],
    }


def call_chat_completion(prompt: str) -> dict[str, Any]:
    api_key = os.getenv("ARK_API_KEY")
    model = os.getenv("ARK_MODEL") or os.getenv("ARK_ENDPOINT_ID")
    base_url = os.getenv("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/")
    if not api_key or not model:
        raise RuntimeError("缺少本地模型环境变量。")

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


def main() -> None:
    parser = argparse.ArgumentParser(description="生成短剧播放器主题和贴图策略 JSON。")
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--highlights", type=Path, default=ROOT / "backend/app/fixtures/reviewed_highlights.json")
    parser.add_argument("--episode-id", type=int, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fallback-only", action="store_true")
    args = parser.parse_args()

    context = load_episode_context(args.input, args.highlights, args.episode_id)
    if args.fallback_only:
        payload = fallback_strategy(context)
        payload["source"] = "local_fallback"
    else:
        try:
            payload = call_chat_completion(build_prompt(context))
            payload["source"] = "llm"
        except Exception as exc:
            payload = fallback_strategy(context)
            payload["source"] = "local_fallback"
            payload["fallback_reason"] = exc.__class__.__name__

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
