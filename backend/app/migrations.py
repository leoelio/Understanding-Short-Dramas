from . import models  # noqa: F401 - register SQLAlchemy models before create_all
from .database import Base, engine


SQLITE_HIGHLIGHT_COLUMNS = {
    "annotation_reason": "TEXT DEFAULT ''",
    "evidence_segment_ids_json": "TEXT DEFAULT '[]'",
    "evidence_text": "TEXT DEFAULT ''",
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
        connection.exec_driver_sql(
            "DELETE FROM interactions WHERE highlight_id NOT IN (SELECT id FROM highlights)"
        )
