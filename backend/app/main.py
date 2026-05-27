import json
import hashlib
from collections import Counter
from pathlib import Path
from uuid import uuid4

from fastapi import Depends, FastAPI, HTTPException
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import func
from sqlalchemy.orm import Session

from .config import APP_NAME, FRONTEND_DIR
from .database import SessionLocal, get_db
from .danmaku_moderation import moderate_danmaku, moderation_rules_payload
from .migrations import ensure_database_schema
from .models import DanmakuComment, Drama, Episode, EpisodeExperienceConfig, Highlight, Interaction
from .schemas import DanmakuCreate, ExperienceConfigUpdate, InteractionCreate
from .seed import seed_from_video_library
from .annotation_schema import validate_annotation_payload
from .taxonomy import normalize_highlight_type, taxonomy_payload


app = FastAPI(title=APP_NAME)


@app.on_event("startup")
def on_startup() -> None:
    ensure_database_schema()
    with SessionLocal() as db:
        seed_from_video_library(db)


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


def highlight_payload(highlight: Highlight) -> dict:
    normalized_type = normalize_highlight_type(highlight.highlight_type)
    return {
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


def danmaku_user_payload(comment: DanmakuComment) -> dict:
    raw = comment.session_id or "anonymous"
    digest = hashlib.sha1(raw.encode("utf-8")).hexdigest()[:8]
    suffix = int(digest[:4], 16) % 1000
    return {
        "id": f"anon-{digest}",
        "nickname": f"游客{suffix:03d}",
        "relation_ready": False,
    }


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
def create_interaction(payload: InteractionCreate, db: Session = Depends(get_db)) -> dict:
    highlight = db.get(Highlight, payload.highlight_id)
    if not highlight:
        raise HTTPException(status_code=404, detail="高光点不存在")
    valid_keys = {option["key"] for option in parse_options(highlight)}
    if payload.option_key not in valid_keys:
        raise HTTPException(status_code=400, detail="互动选项不存在")

    interaction = Interaction(
        highlight_id=payload.highlight_id,
        option_key=payload.option_key,
        session_id=payload.session_id,
    )
    db.add(interaction)
    db.commit()
    return {"ok": True, "highlight_id": highlight.id, "stats": option_stats(db, highlight)}


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
def create_danmaku(payload: DanmakuCreate, db: Session = Depends(get_db)) -> dict:
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


@app.get("/api/stats/summary")
def stats_summary(db: Session = Depends(get_db)) -> dict:
    return {
        "drama_count": db.query(Drama).count(),
        "episode_count": db.query(Episode).count(),
        "highlight_count": db.query(Highlight).count(),
        "interaction_count": db.query(Interaction).count(),
        "danmaku_count": db.query(DanmakuComment).count(),
        "experience_config_count": db.query(EpisodeExperienceConfig).count(),
    }


@app.get("/api/stats/highlights")
def stats_highlights(db: Session = Depends(get_db)) -> list[dict]:
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


@app.get("/api/admin/episodes")
def admin_list_episodes(db: Session = Depends(get_db)) -> list[dict]:
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
def admin_review_status(db: Session = Depends(get_db)) -> dict:
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
def admin_get_episode_highlights(episode_id: int, db: Session = Depends(get_db)) -> dict:
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
def admin_get_episode_experience(episode_id: int, db: Session = Depends(get_db)) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    row = db.query(EpisodeExperienceConfig).filter(EpisodeExperienceConfig.episode_id == episode_id).first()
    return parse_experience_config(row, episode)


@app.put("/api/admin/episodes/{episode_id}/experience")
def admin_save_episode_experience(
    episode_id: int, payload: ExperienceConfigUpdate, db: Session = Depends(get_db)
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
def admin_replace_episode_highlights(episode_id: int, payload: dict, db: Session = Depends(get_db)) -> dict:
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
