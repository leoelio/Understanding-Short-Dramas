from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.request
from collections import defaultdict
from pathlib import Path

from dotenv import load_dotenv


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
load_dotenv(ROOT / ".env")

from backend.app.danmaku_governance import cluster_key  # noqa: E402
from backend.app.database import SessionLocal  # noqa: E402
from backend.app.migrations import ensure_database_schema  # noqa: E402
from backend.app.models import DanmakuComment, Episode  # noqa: E402


def redact(text: str) -> str:
    text = re.sub(r"ark-[A-Za-z0-9-]+", "[redacted_api_key]", text)
    text = re.sub(r"sk-proj-[A-Za-z0-9_-]+", "[redacted_api_key]", text)
    text = re.sub(r"ep-[A-Za-z0-9-]+", "[redacted_endpoint]", text)
    return text


def call_llm(prompt: str) -> dict:
    api_key = os.getenv("ARK_API_KEY") or os.getenv("OPENAI_API_KEY")
    model = os.getenv("ARK_MODEL") or os.getenv("ARK_ENDPOINT_ID") or os.getenv("OPENAI_MODEL", "gpt-4.1")
    base_url = os.getenv("ARK_BASE_URL") or os.getenv("OPENAI_BASE_URL", "https://api.openai.com/v1")
    if not api_key:
        raise RuntimeError("missing API key in local environment")
    body = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": "你是短剧弹幕审核助手。只返回 JSON，不要解释。",
            },
            {"role": "user", "content": prompt},
        ],
        "temperature": 0,
    }
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/chat/completions",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        data = json.loads(response.read().decode("utf-8"))
    content = data["choices"][0]["message"]["content"]
    return json.loads(content)


def build_prompt(episode: Episode, rows: list[DanmakuComment]) -> str:
    samples = [
        {
            "id": row.id,
            "time_sec": row.time_sec,
            "text": row.text,
            "current_status": row.review_status,
            "reason": row.moderation_reason,
        }
        for row in rows
    ]
    return json.dumps(
        {
            "task": "批量判断弹幕是否适合展示。输出 items 数组，每项包含 id, review_status(approved/needs_review/hidden), mode(light/carnival), reason, suggested_time_sec。",
            "episode": {
                "drama_title": episode.drama.title,
                "episode_no": episode.episode_no,
                "duration_sec": episode.duration_sec,
            },
            "criteria": [
                "低俗、广告、联系方式、纯刷屏应 hidden",
                "剧透或时间点提前透露关键剧情应 needs_review，并给 suggested_time_sec",
                "站外平台语境、粉丝打卡、演员无关讨论一般 needs_review 或 hidden",
                "贴合剧情且不剧透的情绪表达 approved",
                "轻量闲聊用 light，高情绪共鸣用 carnival",
            ],
            "items": samples,
        },
        ensure_ascii=False,
    )


def representative_rows(rows: list[DanmakuComment], limit: int) -> list[DanmakuComment]:
    groups: dict[str, list[DanmakuComment]] = defaultdict(list)
    for row in rows:
        groups[cluster_key(row.text)].append(row)
    reps = [max(items, key=lambda row: (row.source_like_count or 0, row.cluster_size or 1)) for items in groups.values()]
    reps.sort(key=lambda row: (0 if row.review_status == "needs_review" else 1, -(row.risk_score or 0), row.time_sec))
    return reps[:limit]


def main() -> None:
    parser = argparse.ArgumentParser(description="Optional LLM semantic review for danmaku clusters.")
    parser.add_argument("--episode-id", type=int, required=True)
    parser.add_argument("--limit", type=int, default=40)
    parser.add_argument("--call-llm", action="store_true")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()

    ensure_database_schema()
    db = SessionLocal()
    try:
        episode = db.get(Episode, args.episode_id)
        if not episode:
            raise SystemExit("episode not found")
        rows = (
            db.query(DanmakuComment)
            .filter(DanmakuComment.episode_id == args.episode_id)
            .order_by(DanmakuComment.time_sec.asc())
            .all()
        )
        reps = representative_rows(rows, args.limit)
        prompt = build_prompt(episode, reps)
        if not args.call_llm:
            print(json.dumps({"mode": "dry_run", "prompt": prompt}, ensure_ascii=False, indent=2))
            return
        result = call_llm(prompt)
        if args.apply:
            by_id = {item["id"]: item for item in result.get("items", [])}
            for row in rows:
                item = by_id.get(row.id)
                if not item:
                    continue
                row.review_status = item.get("review_status", row.review_status)
                row.mode = item.get("mode", row.mode)
                row.moderation_reason = item.get("reason", row.moderation_reason)
                row.suggested_time_sec = item.get("suggested_time_sec", row.suggested_time_sec)
                row.moderation_model_version = "llm-semantic-review-v1"
            db.commit()
        print(json.dumps(result, ensure_ascii=False, indent=2))
    except Exception as exc:
        print(redact(json.dumps({"error": exc.__class__.__name__, "message": str(exc)}, ensure_ascii=False, indent=2)))
        raise
    finally:
        db.close()


if __name__ == "__main__":
    main()
