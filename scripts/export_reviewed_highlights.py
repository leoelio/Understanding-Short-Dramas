import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.database import SessionLocal
from backend.app.migrations import ensure_database_schema
from backend.app.models import Drama, Episode, Highlight


FIXTURE_PATH = ROOT / "backend" / "app" / "fixtures" / "reviewed_highlights.json"


def parse_json_field(value: str | None, fallback):
    if not value:
        return fallback
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return fallback


def highlight_payload(highlight: Highlight) -> dict:
    return {
        "start_time_sec": highlight.start_time_sec,
        "end_time_sec": highlight.end_time_sec,
        "title": highlight.title,
        "description": highlight.description,
        "highlight_type": highlight.highlight_type,
        "emotion": highlight.emotion,
        "confidence": highlight.confidence,
        "source": highlight.source,
        "model_version": highlight.model_version,
        "annotation_reason": highlight.annotation_reason,
        "evidence_segment_ids": parse_json_field(highlight.evidence_segment_ids_json, []),
        "evidence_text": highlight.evidence_text,
        "options": parse_json_field(highlight.options_json, []),
    }


def main() -> None:
    ensure_database_schema()
    payload = {"episodes": []}

    with SessionLocal() as db:
        episodes = (
            db.query(Episode)
            .join(Drama)
            .join(Highlight)
            .filter(Highlight.source == "human_review")
            .order_by(Drama.title.asc(), Episode.episode_no.asc())
            .distinct()
            .all()
        )
        for episode in episodes:
            highlights = (
                db.query(Highlight)
                .filter(Highlight.episode_id == episode.id, Highlight.source == "human_review")
                .order_by(Highlight.start_time_sec.asc(), Highlight.id.asc())
                .all()
            )
            payload["episodes"].append(
                {
                    "drama_title": episode.drama.title,
                    "episode_no": episode.episode_no,
                    "highlights": [highlight_payload(item) for item in highlights],
                }
            )

    FIXTURE_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{FIXTURE_PATH} ({len(payload['episodes'])} episodes)")


if __name__ == "__main__":
    main()
