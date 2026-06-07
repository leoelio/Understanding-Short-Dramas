from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.danmaku_governance import SMALL_MODEL_PATH, train_small_model_from_db  # noqa: E402
from backend.app.database import SessionLocal  # noqa: E402
from backend.app.migrations import ensure_database_schema  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the lightweight danmaku moderation model from reviewed rows.")
    parser.add_argument("--output", type=Path, default=SMALL_MODEL_PATH)
    args = parser.parse_args()

    ensure_database_schema()
    db = SessionLocal()
    try:
        result = train_small_model_from_db(db, args.output)
    finally:
        db.close()
    print(json.dumps({"output": str(args.output), **result}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
