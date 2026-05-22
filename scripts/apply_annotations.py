import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.annotation_schema import validate_annotation_payload
from backend.app.database import SessionLocal
from backend.app.migrations import ensure_database_schema
from backend.app.models import Episode, Highlight, Interaction


def main() -> None:
    parser = argparse.ArgumentParser(description="把复核后的高光标注写回数据库。")
    parser.add_argument("--file", type=Path, required=True, help="标注 JSON 文件。")
    parser.add_argument("--replace", action="store_true", help="替换该剧集已有高光点。")
    parser.add_argument("--source", default="llm_review", help="写入数据库的标注来源。")
    parser.add_argument("--model-version", default="highlight-annotation-v1", help="模型或 Prompt 版本。")
    args = parser.parse_args()

    payload = json.loads(args.file.read_text(encoding="utf-8"))
    errors = validate_annotation_payload(payload)
    if errors:
        raise SystemExit("标注 JSON 校验失败：\n" + "\n".join(f"- {error}" for error in errors))

    ensure_database_schema()
    with SessionLocal() as db:
        episode = db.get(Episode, int(payload["episode_id"]))
        if not episode:
            raise SystemExit(f"剧集不存在：{payload['episode_id']}")

        if args.replace:
            existing_highlight_ids = [
                row[0] for row in db.query(Highlight.id).filter(Highlight.episode_id == episode.id).all()
            ]
            if existing_highlight_ids:
                db.query(Interaction).filter(Interaction.highlight_id.in_(existing_highlight_ids)).delete(
                    synchronize_session=False
                )
            db.query(Highlight).filter(Highlight.episode_id == episode.id).delete(synchronize_session=False)

        for item in payload["highlights"]:
            db.add(
                Highlight(
                    episode_id=episode.id,
                    start_time_sec=float(item["start_time_sec"]),
                    end_time_sec=float(item["end_time_sec"]),
                    title=item["title"],
                    description=item.get("description", ""),
                    highlight_type=item["highlight_type"],
                    emotion=item["emotion"],
                    options_json=json.dumps(item["options"], ensure_ascii=False),
                    source=args.source,
                    confidence=float(item.get("confidence", 0.7)),
                    model_version=args.model_version,
                    annotation_reason=item.get("reason", ""),
                    evidence_segment_ids_json=json.dumps(item.get("evidence_segment_ids", []), ensure_ascii=False),
                    evidence_text=item.get("evidence_text", ""),
                )
            )
        db.commit()
        print(f"已写入 {len(payload['highlights'])} 个高光点：episode_id={episode.id}")


if __name__ == "__main__":
    main()
