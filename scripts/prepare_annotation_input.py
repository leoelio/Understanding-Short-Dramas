import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.config import ROOT_DIR
from backend.app.database import SessionLocal
from backend.app.models import Episode


def build_segments(duration: float, segment_seconds: int) -> list[dict]:
    segments = []
    start = 0
    index = 1
    while start < duration:
        end = min(duration, start + segment_seconds)
        segments.append(
            {
                "segment_id": index,
                "start_time_sec": round(start, 2),
                "end_time_sec": round(end, 2),
                "subtitle_text": "",
                "visual_note": "",
                "audio_note": "",
                "manual_note": "",
            }
        )
        start = end
        index += 1
    return segments


def main() -> None:
    parser = argparse.ArgumentParser(description="生成大模型高光标注输入 JSON。")
    parser.add_argument("--episode-id", type=int, required=True, help="数据库中的剧集 ID。")
    parser.add_argument("--segment-seconds", type=int, default=20, help="切分片段长度，单位秒。")
    parser.add_argument("--output", type=Path, default=None, help="输出文件路径。")
    args = parser.parse_args()

    with SessionLocal() as db:
        episode = db.get(Episode, args.episode_id)
        if not episode:
            raise SystemExit(f"剧集不存在：{args.episode_id}")
        duration = episode.duration_sec or 180
        payload = {
            "episode_id": episode.id,
            "drama_title": episode.drama.title,
            "episode_title": episode.title,
            "duration_sec": duration,
            "video_path": episode.video_path,
            "annotation_hint": "请先人工补充 subtitle_text / visual_note / manual_note，再交给大模型标注。",
            "segments": build_segments(duration, args.segment_seconds),
        }

    output = args.output or ROOT_DIR / "data" / "annotation_inputs" / f"episode_{args.episode_id}.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
