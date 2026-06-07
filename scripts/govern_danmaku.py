from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.danmaku_governance import apply_governance_to_episode  # noqa: E402
from backend.app.database import SessionLocal  # noqa: E402
from backend.app.migrations import ensure_database_schema  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="Run layered danmaku governance.")
    parser.add_argument("--episode-id", type=int, default=None, help="Only process one episode id.")
    args = parser.parse_args()

    ensure_database_schema()
    db = SessionLocal()
    try:
        result = apply_governance_to_episode(db, args.episode_id)
    finally:
        db.close()
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
