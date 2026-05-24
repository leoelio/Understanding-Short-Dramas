import argparse
import math
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.config import ROOT_DIR
from backend.app.database import SessionLocal
from backend.app.models import Episode


def main() -> None:
    parser = argparse.ArgumentParser(description="为人工复核生成剧集关键帧拼图。")
    parser.add_argument("--episode-id", type=int, required=True, help="数据库中的剧集 ID。")
    parser.add_argument("--interval", type=int, default=5, help="抽帧间隔，单位秒。")
    parser.add_argument("--columns", type=int, default=5, help="拼图列数。")
    parser.add_argument("--rows", type=int, default=None, help="拼图行数。默认按剧集时长自动计算。")
    parser.add_argument("--width", type=int, default=300, help="每张小图宽度。")
    parser.add_argument("--output", type=Path, default=None, help="输出图片路径。")
    args = parser.parse_args()

    with SessionLocal() as db:
        episode = db.get(Episode, args.episode_id)
        if not episode:
            raise SystemExit(f"剧集不存在：{args.episode_id}")
        video_path = Path(episode.video_path)
        duration_sec = float(episode.duration_sec or 0)

    if not video_path.exists():
        raise SystemExit(f"视频文件不存在：{video_path}")

    output = args.output or ROOT_DIR / "data" / "context" / f"episode_{args.episode_id}" / f"contact_sheet_{args.interval}s.jpg"
    output.parent.mkdir(parents=True, exist_ok=True)

    if args.rows is None:
        frame_count = max(1, math.floor(duration_sec / args.interval) + 1)
        rows = max(1, math.ceil(frame_count / args.columns))
    else:
        rows = args.rows

    filter_expr = f"fps=1/{args.interval},scale={args.width}:-1,tile={args.columns}x{rows}:padding=5:margin=5"
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-i",
            str(video_path),
            "-vf",
            filter_expr,
            "-frames:v",
            "1",
            "-update",
            "1",
            str(output),
        ],
        check=True,
    )
    print(output)


if __name__ == "__main__":
    main()
