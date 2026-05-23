import json
import re
import subprocess
from pathlib import Path

from sqlalchemy.orm import Session

from .config import SEED_EPISODES_PER_DRAMA, VIDEO_LIBRARY_PATH
from .models import DanmakuComment, Drama, Episode, Highlight


EPISODE_RE = re.compile(r"第(\d+)集")
FIXTURE_PATH = Path(__file__).resolve().parent / "fixtures" / "reviewed_highlights.json"

TYPE_PRESETS = [
    {
        "type": "爽点逆袭",
        "emotion": "爽",
        "title": "剧情爽点来袭",
        "options": [
            {"key": "shuang", "label": "爽"},
            {"key": "again", "label": "再来一次"},
            {"key": "support", "label": "站她"},
        ],
    },
    {
        "type": "反转揭秘",
        "emotion": "震惊",
        "title": "剧情突然反转",
        "options": [
            {"key": "shock", "label": "震惊"},
            {"key": "unexpected", "label": "还能这样"},
            {"key": "rewatch", "label": "倒回去看"},
        ],
    },
    {
        "type": "冲突对抗",
        "emotion": "愤怒",
        "title": "冲突升级",
        "options": [
            {"key": "angry", "label": "气到了"},
            {"key": "fight", "label": "怼回去"},
            {"key": "protect", "label": "护住主角"},
        ],
    },
    {
        "type": "甜蜜心动",
        "emotion": "心动",
        "title": "甜蜜瞬间",
        "options": [
            {"key": "sweet", "label": "磕到了"},
            {"key": "heart", "label": "心动"},
            {"key": "together", "label": "在一起"},
        ],
    },
    {
        "type": "虐心共情",
        "emotion": "心疼",
        "title": "虐心时刻",
        "options": [
            {"key": "hurt", "label": "心疼她"},
            {"key": "cry", "label": "破防了"},
            {"key": "hug", "label": "抱抱"},
        ],
    },
]


def episode_number(path: Path) -> int:
    match = EPISODE_RE.search(path.stem)
    return int(match.group(1)) if match else 99999


def probe_duration(path: Path) -> float:
    try:
        result = subprocess.run(
            [
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                str(path),
            ],
            capture_output=True,
            text=True,
            timeout=12,
            check=True,
        )
        return round(float(result.stdout.strip()), 2)
    except Exception:
        return 0


def build_demo_highlights(episode: Episode, preset_offset: int) -> list[Highlight]:
    duration = episode.duration_sec or 120
    points = [max(8, duration * 0.22), max(18, duration * 0.52), max(28, duration * 0.78)]
    highlights = []
    for index, point in enumerate(points):
        preset = TYPE_PRESETS[(preset_offset + index) % len(TYPE_PRESETS)]
        start = round(point, 2)
        highlights.append(
            Highlight(
                episode_id=episode.id,
                start_time_sec=start,
                end_time_sec=round(min(duration, start + 8), 2),
                title=preset["title"],
                description="演示高光点，后续由大模型标注和人工复核替换。",
                highlight_type=preset["type"],
                emotion=preset["emotion"],
                options_json=json.dumps(preset["options"], ensure_ascii=False),
                source="manual_seed",
                confidence=0.72,
                model_version="seed-v1",
            )
        )
    return highlights


def apply_reviewed_fixtures(db: Session) -> None:
    if not FIXTURE_PATH.exists():
        return

    payload = json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))
    for fixture in payload.get("episodes", []):
        drama = db.query(Drama).filter(Drama.title == fixture["drama_title"]).first()
        if not drama:
            continue
        episode = (
            db.query(Episode)
            .filter(Episode.drama_id == drama.id, Episode.episode_no == int(fixture["episode_no"]))
            .first()
        )
        if not episode:
            continue

        db.query(Highlight).filter(Highlight.episode_id == episode.id).delete(synchronize_session=False)
        for item in fixture.get("highlights", []):
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
                    source=item.get("source", "human_review"),
                    confidence=float(item.get("confidence", 0.8)),
                    model_version=item.get("model_version", "highlight-annotation-v1"),
                    annotation_reason=item.get("annotation_reason", ""),
                    evidence_segment_ids_json=json.dumps(item.get("evidence_segment_ids", []), ensure_ascii=False),
                    evidence_text=item.get("evidence_text", ""),
                )
            )


def seed_demo_danmaku(db: Session) -> None:
    if db.query(DanmakuComment).count() > 0:
        return

    comments = ["来了来了", "这段有点东西", "别眨眼", "前方高能", "这反应真实", "我站主角"]
    episodes = db.query(Episode).order_by(Episode.id.asc()).all()
    for episode in episodes:
        if not episode.duration_sec:
            continue
        baseline_times = [episode.duration_sec * 0.12, episode.duration_sec * 0.38, episode.duration_sec * 0.68]
        for index, time_sec in enumerate(baseline_times):
            db.add(
                DanmakuComment(
                    episode_id=episode.id,
                    time_sec=round(time_sec, 2),
                    text=comments[(episode.id + index) % len(comments)],
                    mode="seed",
                    session_id="seed",
                )
            )

        highlights = sorted(episode.highlights, key=lambda item: item.start_time_sec)[:3]
        for index, highlight in enumerate(highlights):
            db.add(
                DanmakuComment(
                    episode_id=episode.id,
                    time_sec=max(0, round(highlight.start_time_sec - 1.5, 2)),
                    text=["要来了", "这就是高光", "情绪到了"][index % 3],
                    mode="seed",
                    session_id="seed",
                )
            )


def seed_from_video_library(db: Session) -> None:
    if db.query(Drama).count() > 0:
        seed_demo_danmaku(db)
        db.commit()
        return

    if not VIDEO_LIBRARY_PATH.exists():
        raise RuntimeError(f"视频素材目录不存在：{VIDEO_LIBRARY_PATH}")

    drama_dirs = sorted([path for path in VIDEO_LIBRARY_PATH.iterdir() if path.is_dir()], key=lambda p: p.name)
    for drama_index, drama_dir in enumerate(drama_dirs):
        drama = Drama(title=drama_dir.name, genre="待标注", description="从本地视频库导入的短剧素材。")
        db.add(drama)
        db.flush()

        episode_files = sorted(drama_dir.glob("*.mp4"), key=episode_number)
        selected_files = episode_files[:SEED_EPISODES_PER_DRAMA]
        for video_path in selected_files:
            number = episode_number(video_path)
            episode = Episode(
                drama_id=drama.id,
                episode_no=number,
                title=f"第{number}集",
                video_path=str(video_path),
                duration_sec=probe_duration(video_path),
            )
            db.add(episode)
            db.flush()

            for highlight in build_demo_highlights(episode, drama_index):
                db.add(highlight)

    apply_reviewed_fixtures(db)
    seed_demo_danmaku(db)
    db.commit()
