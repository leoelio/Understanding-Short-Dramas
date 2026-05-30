from pathlib import Path
import os

from dotenv import load_dotenv


ROOT_DIR = Path(__file__).resolve().parents[2]
load_dotenv(ROOT_DIR / ".env")


def resolve_path(raw_path: str) -> Path:
    path = Path(raw_path)
    if path.is_absolute():
        return path
    return ROOT_DIR / path


APP_NAME = os.getenv("APP_NAME", "短剧即时互动激发系统")
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./data/app.db")
VIDEO_LIBRARY_PATH = resolve_path(os.getenv("VIDEO_LIBRARY_PATH", "./视频库"))
SEED_EPISODES_PER_DRAMA = int(os.getenv("SEED_EPISODES_PER_DRAMA", "2"))
FRONTEND_DIR = ROOT_DIR / "frontend"
DATA_DIR = ROOT_DIR / "data"
VOICE_ASSET_DIR = resolve_path(os.getenv("VOICE_ASSET_DIR", "./data/voice_assets"))
COSYVOICE_BASE_URL = os.getenv("COSYVOICE_BASE_URL", "http://127.0.0.1:50001").rstrip("/")
COSYVOICE_TIMEOUT_SECONDS = float(os.getenv("COSYVOICE_TIMEOUT_SECONDS", "120"))
