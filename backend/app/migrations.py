from . import models  # noqa: F401 - register SQLAlchemy models before create_all
from .database import Base, engine


SQLITE_HIGHLIGHT_COLUMNS = {
    "annotation_reason": "TEXT DEFAULT ''",
    "evidence_segment_ids_json": "TEXT DEFAULT '[]'",
    "evidence_text": "TEXT DEFAULT ''",
}

SQLITE_EXTRA_COLUMNS = {
    "users": {"avatar_url": "TEXT DEFAULT ''"},
    "interactions": {"user_id": "INTEGER"},
    "danmaku_comments": {
        "user_id": "INTEGER",
        "original_text": "TEXT DEFAULT ''",
        "source_like_count": "INTEGER DEFAULT 0",
        "review_status": "TEXT DEFAULT 'approved'",
        "risk_score": "FLOAT DEFAULT 0",
        "quality_score": "FLOAT DEFAULT 0.7",
        "spoiler_score": "FLOAT DEFAULT 0",
        "relevance_score": "FLOAT DEFAULT 0.7",
        "cluster_key": "TEXT DEFAULT ''",
        "cluster_size": "INTEGER DEFAULT 1",
        "suggested_time_sec": "FLOAT",
        "moderation_model_version": "TEXT DEFAULT ''",
        "moderation_layers_json": "TEXT DEFAULT '{}'",
        "moderation_reason": "TEXT DEFAULT ''",
    },
    "episode_ai_remixes": {"featured_order": "INTEGER DEFAULT 0 NOT NULL"},
}


def ensure_database_schema() -> None:
    Base.metadata.create_all(bind=engine)
    if engine.dialect.name != "sqlite":
        return

    with engine.begin() as connection:
        rows = connection.exec_driver_sql("PRAGMA table_info(highlights)").fetchall()
        existing_columns = {row[1] for row in rows}
        for column_name, column_type in SQLITE_HIGHLIGHT_COLUMNS.items():
            if column_name not in existing_columns:
                connection.exec_driver_sql(f"ALTER TABLE highlights ADD COLUMN {column_name} {column_type}")
        for table_name, columns in SQLITE_EXTRA_COLUMNS.items():
            rows = connection.exec_driver_sql(f"PRAGMA table_info({table_name})").fetchall()
            existing_columns = {row[1] for row in rows}
            for column_name, column_type in columns.items():
                if column_name not in existing_columns:
                    connection.exec_driver_sql(f"ALTER TABLE {table_name} ADD COLUMN {column_name} {column_type}")
        connection.exec_driver_sql(
            "DELETE FROM interactions WHERE highlight_id NOT IN (SELECT id FROM highlights)"
        )
