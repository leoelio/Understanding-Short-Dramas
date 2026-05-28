import json
import hashlib
from collections import Counter
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import func
from sqlalchemy.orm import Session

from .config import APP_NAME, FRONTEND_DIR
from .database import SessionLocal, get_db
from .danmaku_moderation import moderate_danmaku, moderation_rules_payload
from .migrations import ensure_database_schema
from .models import (
    AuthSession,
    DanmakuComment,
    Drama,
    Episode,
    EpisodeExperienceConfig,
    Highlight,
    Interaction,
    User,
    UserReward,
    WatchHistory,
    WatchRoom,
)
from .schemas import (
    DanmakuCreate,
    ExperienceConfigUpdate,
    InteractionCreate,
    LoginRequest,
    RegisterRequest,
    UserAdminUpdate,
    WatchHistoryUpdate,
    WatchRoomCreate,
    WatchRoomJoin,
    WatchRoomSync,
)
from .seed import seed_from_video_library
from .annotation_schema import validate_annotation_payload
from .auth import (
    create_session,
    ensure_default_users,
    get_current_user,
    get_optional_user,
    hash_password,
    public_user,
    require_roles,
    token_digest,
    verify_password,
)
from .taxonomy import normalize_highlight_type, taxonomy_payload


app = FastAPI(title=APP_NAME)


@app.on_event("startup")
def on_startup() -> None:
    ensure_database_schema()
    with SessionLocal() as db:
        seed_from_video_library(db)
        ensure_default_users(db)


def parse_options(highlight: Highlight) -> list[dict]:
    try:
        return json.loads(highlight.options_json)
    except json.JSONDecodeError:
        return []


def parse_evidence_segment_ids(highlight: Highlight) -> list[int | str]:
    try:
        values = json.loads(highlight.evidence_segment_ids_json or "[]")
    except json.JSONDecodeError:
        return []
    return [value for value in values if isinstance(value, (int, float, str)) and str(value)]


PREDICTION_REWARD_RULES = [
    {
        "drama_title": "那年冬至",
        "episode_no": 1,
        "highlight_title": "男主会选择哪一个",
        "correct_option_key": "reality",
        "correct_label": "选二",
        "points": 20,
        "reward_key": "winter_choice_oracle",
        "title": "冬至预言家",
        "description": "在同看竞猜中猜中男主会选择第二个选项。",
    }
]


def prediction_reward_rule(highlight: Highlight) -> dict | None:
    episode = highlight.episode
    if not episode or not episode.drama:
        return None
    for rule in PREDICTION_REWARD_RULES:
        if (
            rule["drama_title"] == episode.drama.title
            and int(rule["episode_no"]) == int(episode.episode_no)
            and rule["highlight_title"] == highlight.title
        ):
            return rule
    return None


def highlight_payload(highlight: Highlight) -> dict:
    normalized_type = normalize_highlight_type(highlight.highlight_type)
    reward_rule = prediction_reward_rule(highlight)
    payload = {
        "id": highlight.id,
        "start_time_sec": highlight.start_time_sec,
        "end_time_sec": highlight.end_time_sec,
        "title": highlight.title,
        "description": highlight.description,
        "highlight_type": normalized_type,
        "raw_highlight_type": highlight.highlight_type,
        "emotion": highlight.emotion,
        "options": parse_options(highlight),
        "source": highlight.source,
        "confidence": highlight.confidence,
        "model_version": highlight.model_version,
        "annotation_reason": highlight.annotation_reason,
        "evidence_segment_ids": parse_evidence_segment_ids(highlight),
        "evidence_text": highlight.evidence_text,
    }
    if reward_rule:
        payload["reward_hint"] = {
            "kind": "prediction",
            "points": reward_rule["points"],
            "title": reward_rule["title"],
            "description": "同看竞猜题，答对后解锁称号和积分。",
        }
    return payload


def annotation_item_payload(highlight: Highlight) -> dict:
    payload = highlight_payload(highlight)
    return {
        "start_time_sec": payload["start_time_sec"],
        "end_time_sec": payload["end_time_sec"],
        "title": payload["title"],
        "description": payload["description"],
        "highlight_type": payload["highlight_type"],
        "emotion": payload["emotion"],
        "confidence": payload["confidence"],
        "reason": payload["annotation_reason"],
        "evidence_segment_ids": payload["evidence_segment_ids"],
        "evidence_text": payload["evidence_text"],
        "options": payload["options"],
    }


def episode_review_meta(episode: Episode) -> dict:
    source_counts = Counter(highlight.source for highlight in episode.highlights)
    reviewed_highlight_count = source_counts.get("human_review", 0)
    review_status = "reviewed" if reviewed_highlight_count else "pending"
    return {
        "highlight_count": len(episode.highlights),
        "reviewed_highlight_count": reviewed_highlight_count,
        "seed_highlight_count": source_counts.get("manual_seed", 0),
        "review_status": review_status,
        "review_status_label": "已复核" if review_status == "reviewed" else "待复核",
        "sources": dict(source_counts),
    }


def option_stats(db: Session, highlight: Highlight) -> dict:
    interactions = db.query(Interaction).filter(Interaction.highlight_id == highlight.id).all()
    counts = Counter(item.option_key for item in interactions)
    total = sum(counts.values())
    options = []
    for option in parse_options(highlight):
        count = counts.get(option["key"], 0)
        options.append(
            {
                "key": option["key"],
                "label": option["label"],
                "count": count,
                "percent": round(count * 100 / total, 1) if total else 0,
            }
        )
    return {"total": total, "options": options}


def reward_payload(reward: UserReward) -> dict:
    return {
        "id": reward.id,
        "reward_key": reward.reward_key,
        "title": reward.title,
        "description": reward.description,
        "points": reward.points,
        "highlight_id": reward.highlight_id,
        "created_at": reward.created_at.isoformat() if reward.created_at else None,
    }


def reward_profile(user: User, db: Session) -> dict:
    rewards = (
        db.query(UserReward)
        .filter(UserReward.user_id == user.id)
        .order_by(UserReward.created_at.desc(), UserReward.id.desc())
        .all()
    )
    points = sum(item.points or 0 for item in rewards)
    title = "剧情新人"
    if points >= 80:
        title = "高光收藏家"
    elif points >= 40:
        title = "剧情读心者"
    elif points >= 20:
        title = "冬至预言家"
    return {
        "points": points,
        "title": title,
        "badges": [reward_payload(item) for item in rewards],
    }


def evaluate_interaction_reward(
    db: Session, user: User | None, highlight: Highlight, option_key: str
) -> dict | None:
    rule = prediction_reward_rule(highlight)
    if not rule:
        return None
    correct = option_key == rule["correct_option_key"]
    base = {
        "kind": "prediction",
        "correct": correct,
        "correct_option_key": rule["correct_option_key"],
        "correct_option_label": rule["correct_label"],
        "points": 0,
    }
    if not correct:
        return {**base, "message": f"差一点，正确答案是{rule['correct_label']}。"}
    if not user:
        return {**base, "message": "答对了。登录后可以领取称号和积分。"}
    reward_key = f"{rule['reward_key']}:{highlight.id}"
    existing = (
        db.query(UserReward)
        .filter(UserReward.user_id == user.id, UserReward.reward_key == reward_key)
        .first()
    )
    if existing:
        return {
            **base,
            "points": 0,
            "already_awarded": True,
            "badge": reward_payload(existing),
            "message": f"你已拥有「{existing.title}」。",
        }
    reward = UserReward(
        user_id=user.id,
        highlight_id=highlight.id,
        reward_key=reward_key,
        title=rule["title"],
        description=rule["description"],
        points=rule["points"],
    )
    db.add(reward)
    db.commit()
    db.refresh(reward)
    return {
        **base,
        "points": reward.points,
        "already_awarded": False,
        "badge": reward_payload(reward),
        "message": f"预判成功，解锁「{reward.title}」+{reward.points}分。",
    }


def danmaku_user_payload(comment: DanmakuComment) -> dict:
    if comment.user:
        return {
            "id": f"user-{comment.user.id}",
            "nickname": comment.user.display_name,
            "role": comment.user.role,
            "relation_ready": True,
        }
    raw = comment.session_id or "anonymous"
    digest = hashlib.sha1(raw.encode("utf-8")).hexdigest()[:8]
    suffix = int(digest[:4], 16) % 1000
    return {
        "id": f"anon-{digest}",
        "nickname": f"游客{suffix:03d}",
        "relation_ready": False,
    }


def user_brief(user: User | None) -> dict | None:
    if not user:
        return None
    return {
        "id": user.id,
        "display_name": user.display_name,
        "role": user.role,
    }


def normalize_room_code(code: str) -> str:
    return "".join(code.strip().upper().split())


def normalize_playback_state(value: str) -> str:
    return "playing" if value == "playing" else "paused"


def safe_episode_progress(episode: Episode | None, progress_sec: float) -> float:
    if not episode:
        return 0
    return round(min(max(0, float(progress_sec)), float(episode.duration_sec or progress_sec)), 2)


def make_room_code(db: Session) -> str:
    for _ in range(12):
        code = uuid4().hex[:6].upper()
        if not db.query(WatchRoom).filter(WatchRoom.code == code).first():
            return code
    return uuid4().hex[:8].upper()


def room_payload(room: WatchRoom, current_user: User) -> dict:
    return {
        "code": room.code,
        "host": user_brief(room.host),
        "guest": user_brief(room.guest),
        "member_count": 1 + int(bool(room.guest_user_id)),
        "is_host": room.host_user_id == current_user.id,
        "episode_id": room.episode_id,
        "progress_sec": room.progress_sec,
        "playback_state": room.playback_state,
        "updated_by": user_brief(room.updated_by),
        "updated_at": room.updated_at.isoformat() if room.updated_at else None,
        "created_at": room.created_at.isoformat() if room.created_at else None,
    }


def get_watch_room(code: str, db: Session) -> WatchRoom:
    room = db.query(WatchRoom).filter(WatchRoom.code == normalize_room_code(code)).first()
    if not room:
        raise HTTPException(status_code=404, detail="同看房间不存在")
    return room


def ensure_room_member(room: WatchRoom, user: User) -> None:
    if user.id not in {room.host_user_id, room.guest_user_id}:
        raise HTTPException(status_code=403, detail="你还没有加入这个同看房间")


def default_experience_config(episode: Episode) -> dict:
    base = {
        "player_theme": {
            "theme_key": "road" if "北往" in episode.drama.title else "city",
            "name": "北往·返乡烟火" if "北往" in episode.drama.title else "都市情绪场",
            "badge": "返乡烟火 · 年三十" if "北往" in episode.drama.title else "默认互动主题",
            "signal": "讨薪现金 / 家用电话 / 路边烟雾 / 行李摩托" if "北往" in episode.drama.title else "高光时间轴 / 弹幕反馈 / 互动按钮",
            "accent": "#ff7a30" if "北往" in episode.drama.title else "#ff4f64",
            "soft": "#f2e2c4" if "北往" in episode.drama.title else "#12d6b0",
            "endpoint_label": "家" if "北往" in episode.drama.title else "",
        },
        "sticker_timeline": [],
        "danmaku_modes": {
            "light": {"label": "轻聊", "enabled": True, "density": 0.72, "include_modes": ["light"]},
            "carnival": {
                "label": "狂欢",
                "enabled": True,
                "density": 1,
                "include_modes": ["light", "curated", "seed", "carnival"],
            },
            "immerse": {"label": "沉浸", "enabled": False, "density": 0, "include_modes": []},
        },
    }
    return base


def parse_experience_config(row: EpisodeExperienceConfig | None, episode: Episode) -> dict:
    if not row:
        return {
            "episode_id": episode.id,
            "drama_title": episode.drama.title,
            "episode_title": episode.title,
            "version": 1,
            "source": "system_default",
            "model_version": "experience-config-v1",
            "review_status": "draft",
            "persisted": False,
            "config": default_experience_config(episode),
        }
    try:
        config = json.loads(row.config_json)
    except json.JSONDecodeError:
        config = {}
    return {
        "episode_id": episode.id,
        "drama_title": episode.drama.title,
        "episode_title": episode.title,
        "version": row.version,
        "source": row.source,
        "model_version": row.model_version,
        "review_status": row.review_status,
        "persisted": True,
        "updated_at": row.updated_at.isoformat() if row.updated_at else None,
        "config": config,
    }


@app.get("/api/health")
def health() -> dict:
    return {"ok": True, "request_id": str(uuid4())}


@app.post("/api/auth/register")
def register(payload: RegisterRequest, db: Session = Depends(get_db)) -> dict:
    username = payload.username.strip().lower()
    if db.query(User).filter(User.username == username).first():
        raise HTTPException(status_code=400, detail="用户名已存在")
    user = User(
        username=username,
        display_name=payload.display_name.strip(),
        password_hash=hash_password(payload.password),
        role="user",
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    token = create_session(db, user)
    return {"token": token, "user": public_user(user)}


@app.post("/api/auth/login")
def login(payload: LoginRequest, db: Session = Depends(get_db)) -> dict:
    username = payload.username.strip().lower()
    user = db.query(User).filter(User.username == username).first()
    if not user or not verify_password(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="用户名或密码错误")
    if not user.is_active:
        raise HTTPException(status_code=403, detail="账号不可用")
    token = create_session(db, user)
    return {"token": token, "user": public_user(user)}


@app.get("/api/auth/me")
def me(user: User = Depends(get_current_user)) -> dict:
    return {"user": public_user(user)}


@app.post("/api/auth/logout")
def logout(
    user: User = Depends(get_current_user),
    authorization: str | None = Header(default=None),
    db: Session = Depends(get_db),
) -> dict:
    # Authorization is parsed manually here to remove only the current session.
    token = None
    if authorization:
        scheme, _, raw_token = authorization.partition(" ")
        if scheme.lower() == "bearer":
            token = raw_token
    if token:
        db.query(AuthSession).filter(AuthSession.user_id == user.id, AuthSession.token_hash == token_digest(token)).delete()
        db.commit()
    return {"ok": True}


@app.get("/api/taxonomy/highlights")
def highlight_taxonomy() -> list[dict]:
    return taxonomy_payload()


@app.get("/api/dramas")
def list_dramas(db: Session = Depends(get_db)) -> list[dict]:
    dramas = db.query(Drama).order_by(Drama.id.asc()).all()
    payload = []
    for drama in dramas:
        episodes = sorted(drama.episodes, key=lambda item: item.episode_no)
        first_episode = episodes[0] if episodes else None
        payload.append(
            {
                "id": drama.id,
                "title": drama.title,
                "genre": drama.genre,
                "description": drama.description,
                "episode_count": len(episodes),
                "first_episode_id": first_episode.id if first_episode else None,
                "preview_video_url": f"/media/episodes/{first_episode.id}" if first_episode else None,
            }
        )
    return payload


@app.get("/api/dramas/{drama_id}/episodes")
def list_episodes(drama_id: int, db: Session = Depends(get_db)) -> list[dict]:
    drama = db.get(Drama, drama_id)
    if not drama:
        raise HTTPException(status_code=404, detail="短剧不存在")
    episodes = db.query(Episode).filter(Episode.drama_id == drama_id).order_by(Episode.episode_no.asc()).all()
    return [
        {
            "id": episode.id,
            "episode_no": episode.episode_no,
            "title": episode.title,
            "duration_sec": episode.duration_sec,
            "video_url": f"/media/episodes/{episode.id}",
        }
        for episode in episodes
    ]


@app.get("/api/episodes/{episode_id}")
def get_episode(episode_id: int, db: Session = Depends(get_db)) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    highlights = (
        db.query(Highlight)
        .filter(Highlight.episode_id == episode_id)
        .order_by(Highlight.start_time_sec.asc())
        .all()
    )
    return {
        "id": episode.id,
        "episode_no": episode.episode_no,
        "title": episode.title,
        "duration_sec": episode.duration_sec,
        "video_url": f"/media/episodes/{episode.id}",
        "drama": {"id": episode.drama.id, "title": episode.drama.title, "genre": episode.drama.genre},
        "highlights": [highlight_payload(item) for item in highlights],
    }


@app.get("/media/episodes/{episode_id}")
def episode_media(episode_id: int, db: Session = Depends(get_db)) -> FileResponse:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    path = Path(episode.video_path)
    if not path.exists():
        raise HTTPException(status_code=404, detail="视频文件不存在")
    return FileResponse(path, media_type="video/mp4", filename=path.name)


@app.post("/api/interactions")
def create_interaction(
    payload: InteractionCreate,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
) -> dict:
    highlight = db.get(Highlight, payload.highlight_id)
    if not highlight:
        raise HTTPException(status_code=404, detail="高光点不存在")
    valid_keys = {option["key"] for option in parse_options(highlight)}
    if payload.option_key not in valid_keys:
        raise HTTPException(status_code=400, detail="互动选项不存在")

    interaction = Interaction(
        highlight_id=payload.highlight_id,
        user_id=user.id if user else None,
        option_key=payload.option_key,
        session_id=payload.session_id,
    )
    db.add(interaction)
    db.commit()
    reward = evaluate_interaction_reward(db, user, highlight, payload.option_key)
    return {"ok": True, "highlight_id": highlight.id, "stats": option_stats(db, highlight), "reward": reward}


@app.get("/api/episodes/{episode_id}/danmaku")
def list_danmaku(episode_id: int, db: Session = Depends(get_db)) -> list[dict]:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    rows = (
        db.query(DanmakuComment)
        .filter(DanmakuComment.episode_id == episode_id)
        .order_by(DanmakuComment.time_sec.asc(), DanmakuComment.id.asc())
        .all()
    )
    return [
        {
            "id": row.id,
            "episode_id": row.episode_id,
            "time_sec": row.time_sec,
            "text": row.text,
            "mode": row.mode,
            "user": danmaku_user_payload(row),
            "created_at": row.created_at.isoformat() if row.created_at else None,
        }
        for row in rows
    ]


@app.get("/api/episodes/{episode_id}/experience")
def get_episode_experience(episode_id: int, db: Session = Depends(get_db)) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    row = db.query(EpisodeExperienceConfig).filter(EpisodeExperienceConfig.episode_id == episode_id).first()
    return parse_experience_config(row, episode)


@app.get("/api/danmaku/moderation-rules")
def danmaku_moderation_rules() -> dict:
    return moderation_rules_payload()


@app.post("/api/danmaku")
def create_danmaku(
    payload: DanmakuCreate,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
) -> dict:
    episode = db.get(Episode, payload.episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    text = payload.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="弹幕不能为空")
    safe_time = min(float(payload.time_sec), float(episode.duration_sec or payload.time_sec))
    moderation = moderate_danmaku(text, episode.id, safe_time)
    if not moderation.allowed:
        raise HTTPException(
            status_code=400,
            detail={"category": moderation.category, "message": moderation.message},
        )
    comment = DanmakuComment(
        episode_id=episode.id,
        user_id=user.id if user else None,
        time_sec=round(max(0, safe_time), 2),
        text=moderation.text,
        session_id=payload.session_id,
        mode=payload.mode,
    )
    db.add(comment)
    db.commit()
    db.refresh(comment)
    return {
        "id": comment.id,
        "episode_id": comment.episode_id,
        "time_sec": comment.time_sec,
        "text": comment.text,
        "mode": comment.mode,
        "user": danmaku_user_payload(comment),
        "created_at": comment.created_at.isoformat() if comment.created_at else None,
    }


@app.get("/api/users/me/watch-history")
def get_watch_history(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> list[dict]:
    rows = (
        db.query(WatchHistory)
        .filter(WatchHistory.user_id == user.id)
        .order_by(WatchHistory.updated_at.desc())
        .limit(12)
        .all()
    )
    return [
        {
            "episode_id": row.episode_id,
            "progress_sec": row.progress_sec,
            "duration_sec": row.episode.duration_sec,
            "progress_percent": round(row.progress_sec * 100 / row.episode.duration_sec, 1)
            if row.episode.duration_sec
            else 0,
            "updated_at": row.updated_at.isoformat() if row.updated_at else None,
            "episode_title": row.episode.title,
            "episode_no": row.episode.episode_no,
            "drama": {
                "id": row.episode.drama.id,
                "title": row.episode.drama.title,
                "genre": row.episode.drama.genre,
            },
        }
        for row in rows
    ]


@app.post("/api/users/me/watch-history")
def upsert_watch_history(
    payload: WatchHistoryUpdate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    episode = db.get(Episode, payload.episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    row = (
        db.query(WatchHistory)
        .filter(WatchHistory.user_id == user.id, WatchHistory.episode_id == episode.id)
        .first()
    )
    if not row:
        row = WatchHistory(user_id=user.id, episode_id=episode.id)
        db.add(row)
    row.progress_sec = min(float(payload.progress_sec), float(episode.duration_sec or payload.progress_sec))
    row.updated_at = datetime.utcnow()
    db.commit()
    return {"ok": True}


@app.get("/api/users/me/rewards")
def get_my_rewards(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> dict:
    return reward_profile(user, db)


@app.post("/api/watch-rooms")
def create_watch_room(
    payload: WatchRoomCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    episode = db.get(Episode, payload.episode_id) if payload.episode_id else None
    if payload.episode_id and not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    room = WatchRoom(
        code=make_room_code(db),
        host_user_id=user.id,
        episode_id=episode.id if episode else None,
        progress_sec=safe_episode_progress(episode, payload.progress_sec),
        playback_state=normalize_playback_state(payload.playback_state),
        updated_by_user_id=user.id,
    )
    db.add(room)
    db.commit()
    db.refresh(room)
    return room_payload(room, user)


@app.post("/api/watch-rooms/join")
def join_watch_room(
    payload: WatchRoomJoin,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    room = get_watch_room(payload.code, db)
    if user.id != room.host_user_id and room.guest_user_id not in {None, user.id}:
        raise HTTPException(status_code=409, detail="房间已满")
    if user.id != room.host_user_id and room.guest_user_id is None:
        room.guest_user_id = user.id
        room.updated_at = datetime.utcnow()
        db.commit()
        db.refresh(room)
    return room_payload(room, user)


@app.get("/api/watch-rooms/{code}")
def get_room_state(
    code: str,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    room = get_watch_room(code, db)
    ensure_room_member(room, user)
    return room_payload(room, user)


@app.post("/api/watch-rooms/{code}/sync")
def sync_watch_room(
    code: str,
    payload: WatchRoomSync,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    room = get_watch_room(code, db)
    ensure_room_member(room, user)
    episode = db.get(Episode, payload.episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    room.episode_id = episode.id
    room.progress_sec = safe_episode_progress(episode, payload.progress_sec)
    room.playback_state = normalize_playback_state(payload.playback_state)
    room.updated_by_user_id = user.id
    room.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(room)
    return room_payload(room, user)


@app.get("/api/stats/summary")
def stats_summary(
    db: Session = Depends(get_db), _user: User = Depends(require_roles("admin", "reviewer"))
) -> dict:
    return {
        "drama_count": db.query(Drama).count(),
        "episode_count": db.query(Episode).count(),
        "highlight_count": db.query(Highlight).count(),
        "interaction_count": db.query(Interaction).count(),
        "danmaku_count": db.query(DanmakuComment).count(),
        "experience_config_count": db.query(EpisodeExperienceConfig).count(),
    }


@app.get("/api/stats/highlights")
def stats_highlights(
    db: Session = Depends(get_db), _user: User = Depends(require_roles("admin", "reviewer"))
) -> list[dict]:
    highlights = (
        db.query(Highlight)
        .join(Episode)
        .join(Drama)
        .outerjoin(Interaction)
        .group_by(Highlight.id)
        .order_by(func.count(Interaction.id).desc(), Highlight.id.asc())
        .all()
    )
    rows = []
    for highlight in highlights:
        rows.append(
            {
                **highlight_payload(highlight),
                "drama_title": highlight.episode.drama.title,
                "episode_title": highlight.episode.title,
                "stats": option_stats(db, highlight),
            }
        )
    return rows


def admin_user_payload(user: User, db: Session) -> dict:
    now = datetime.utcnow()
    return {
        "id": user.id,
        "username": user.username,
        "display_name": user.display_name,
        "role": user.role,
        "is_active": user.is_active,
        "created_at": user.created_at.isoformat() if user.created_at else None,
        "active_session_count": db.query(AuthSession)
        .filter(AuthSession.user_id == user.id, AuthSession.expires_at > now)
        .count(),
        "watch_history_count": db.query(WatchHistory).filter(WatchHistory.user_id == user.id).count(),
        "interaction_count": db.query(Interaction).filter(Interaction.user_id == user.id).count(),
        "danmaku_count": db.query(DanmakuComment).filter(DanmakuComment.user_id == user.id).count(),
    }


@app.get("/api/admin/users")
def admin_list_users(
    db: Session = Depends(get_db), _user: User = Depends(require_roles("admin"))
) -> list[dict]:
    users = db.query(User).order_by(User.id.asc()).all()
    return [admin_user_payload(user, db) for user in users]


@app.patch("/api/admin/users/{user_id}")
def admin_update_user(
    user_id: int,
    payload: UserAdminUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(require_roles("admin")),
) -> dict:
    user = db.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="用户不存在")
    if payload.role is not None and payload.role not in {"user", "reviewer", "admin"}:
        raise HTTPException(status_code=400, detail="角色只能是 user、reviewer 或 admin")
    if user.id == current_user.id:
        if payload.role is not None and payload.role != "admin":
            raise HTTPException(status_code=400, detail="不能降低自己的管理员权限")
        if payload.is_active is False:
            raise HTTPException(status_code=400, detail="不能停用当前登录的管理员账号")
    if payload.display_name is not None:
        user.display_name = payload.display_name.strip()
    if payload.role is not None:
        user.role = payload.role
    if payload.is_active is not None:
        user.is_active = payload.is_active
    db.commit()
    db.refresh(user)
    return admin_user_payload(user, db)


@app.get("/api/admin/episodes")
def admin_list_episodes(
    db: Session = Depends(get_db), _user: User = Depends(require_roles("admin", "reviewer"))
) -> list[dict]:
    episodes = db.query(Episode).join(Drama).order_by(Drama.id.asc(), Episode.episode_no.asc()).all()
    rows = []
    for episode in episodes:
        review_meta = episode_review_meta(episode)
        rows.append(
            {
                "id": episode.id,
                "drama_title": episode.drama.title,
                "episode_title": episode.title,
                "episode_no": episode.episode_no,
                "duration_sec": episode.duration_sec,
                "experience_config_status": episode.experience_config.review_status
                if episode.experience_config
                else "draft",
                "experience_config_source": episode.experience_config.source if episode.experience_config else "none",
                "experience_config_version": episode.experience_config.version if episode.experience_config else 0,
                **review_meta,
            }
        )
    return rows


@app.get("/api/admin/review-status")
def admin_review_status(
    db: Session = Depends(get_db), _user: User = Depends(require_roles("admin", "reviewer"))
) -> dict:
    episodes = db.query(Episode).all()
    metas = [episode_review_meta(episode) for episode in episodes]
    reviewed_episode_count = sum(1 for meta in metas if meta["review_status"] == "reviewed")
    return {
        "episode_count": len(episodes),
        "reviewed_episode_count": reviewed_episode_count,
        "pending_episode_count": len(episodes) - reviewed_episode_count,
        "highlight_count": sum(meta["highlight_count"] for meta in metas),
        "reviewed_highlight_count": sum(meta["reviewed_highlight_count"] for meta in metas),
        "seed_highlight_count": sum(meta["seed_highlight_count"] for meta in metas),
    }


@app.get("/api/admin/episodes/{episode_id}/highlights")
def admin_get_episode_highlights(
    episode_id: int,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    highlights = (
        db.query(Highlight)
        .filter(Highlight.episode_id == episode_id)
        .order_by(Highlight.start_time_sec.asc())
        .all()
    )
    return {
        "episode_id": episode.id,
        "drama_title": episode.drama.title,
        "episode_title": episode.title,
        "duration_sec": episode.duration_sec,
        "prompt_version": "admin-review-v1",
        "highlights": [annotation_item_payload(highlight) for highlight in highlights],
    }


@app.get("/api/admin/episodes/{episode_id}/experience")
def admin_get_episode_experience(
    episode_id: int,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    row = db.query(EpisodeExperienceConfig).filter(EpisodeExperienceConfig.episode_id == episode_id).first()
    return parse_experience_config(row, episode)


@app.put("/api/admin/episodes/{episode_id}/experience")
def admin_save_episode_experience(
    episode_id: int,
    payload: ExperienceConfigUpdate,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    if not isinstance(payload.config, dict):
        raise HTTPException(status_code=400, detail="config 必须是 JSON 对象")

    row = db.query(EpisodeExperienceConfig).filter(EpisodeExperienceConfig.episode_id == episode_id).first()
    if not row:
        row = EpisodeExperienceConfig(episode_id=episode_id)
        db.add(row)
    row.version = payload.version
    row.source = payload.source
    row.model_version = payload.model_version
    row.review_status = payload.review_status
    row.config_json = json.dumps(payload.config, ensure_ascii=False)
    db.commit()
    db.refresh(row)
    return parse_experience_config(row, episode)


@app.put("/api/admin/episodes/{episode_id}/highlights")
def admin_replace_episode_highlights(
    episode_id: int,
    payload: dict,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")

    normalized_payload = {
        "episode_id": episode_id,
        "highlights": payload.get("highlights", []),
    }
    errors = validate_annotation_payload(normalized_payload)
    if errors:
        raise HTTPException(status_code=400, detail={"errors": errors})

    source = payload.get("source", "human_review")
    model_version = payload.get("model_version", payload.get("prompt_version", "admin-review-v1"))
    existing_ids = [row[0] for row in db.query(Highlight.id).filter(Highlight.episode_id == episode.id).all()]
    if existing_ids:
        db.query(Interaction).filter(Interaction.highlight_id.in_(existing_ids)).delete(synchronize_session=False)
    db.query(Highlight).filter(Highlight.episode_id == episode.id).delete(synchronize_session=False)

    for item in normalized_payload["highlights"]:
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
                source=source,
                confidence=float(item.get("confidence", 0.7)),
                model_version=model_version,
                annotation_reason=item.get("reason", ""),
                evidence_segment_ids_json=json.dumps(item.get("evidence_segment_ids", []), ensure_ascii=False),
                evidence_text=item.get("evidence_text", ""),
            )
        )

    db.commit()
    return {"ok": True, "episode_id": episode.id, "highlight_count": len(normalized_payload["highlights"])}


if FRONTEND_DIR.exists():
    app.mount("/", StaticFiles(directory=FRONTEND_DIR, html=True), name="frontend")
