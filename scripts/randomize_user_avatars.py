import random
import sqlite3
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
DATABASE_PATH = ROOT_DIR / "data" / "app.db"
AVATAR_POOL_DIR = ROOT_DIR / "avatars"
AVATAR_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}


def main() -> None:
    if not DATABASE_PATH.exists():
        raise SystemExit(f"Database not found: {DATABASE_PATH}")
    if not AVATAR_POOL_DIR.exists():
        raise SystemExit(f"Avatar pool not found: {AVATAR_POOL_DIR}")

    avatars = sorted(
        path
        for path in AVATAR_POOL_DIR.iterdir()
        if path.is_file() and path.suffix.lower() in AVATAR_EXTENSIONS
    )
    if not avatars:
        raise SystemExit("No avatar files found")

    random.SystemRandom().shuffle(avatars)
    with sqlite3.connect(DATABASE_PATH) as conn:
        users = conn.execute("SELECT id, username FROM users ORDER BY id").fetchall()
        for index, (user_id, _username) in enumerate(users):
            avatar = avatars[index % len(avatars)]
            conn.execute(
                "UPDATE users SET avatar_url = ? WHERE id = ?",
                (f"/media/avatar-pool/{avatar.name}", user_id),
            )
        conn.commit()

    print(f"updated {len(users)} users with {len(avatars)} avatar assets")


if __name__ == "__main__":
    main()
