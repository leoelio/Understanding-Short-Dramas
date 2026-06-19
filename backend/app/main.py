import json
import hashlib
import os
import re
import shutil
import subprocess
import urllib.request
from collections import Counter
from datetime import datetime
from pathlib import Path
from uuid import uuid4

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from fastapi.staticfiles import StaticFiles
from sqlalchemy import and_, func, or_
from sqlalchemy.orm import Session

from .config import APP_NAME, COSYVOICE_BASE_URL, COSYVOICE_TIMEOUT_SECONDS, DATA_DIR, FRONTEND_DIR, ROOT_DIR, VOICE_ASSET_DIR
from .database import SessionLocal, get_db
from .danmaku_governance import SMALL_MODEL_PATH, apply_governance_to_episode, evaluate_comment, train_small_model_from_db
from .danmaku_moderation import moderate_danmaku, moderation_rules_payload
from .migrations import ensure_database_schema
from .models import (
    AuthSession,
    ChatMessage,
    DanmakuComment,
    Drama,
    EpisodeAIRemix,
    Episode,
    EpisodeExperienceConfig,
    Highlight,
    Interaction,
    SocialComment,
    SocialNotification,
    SocialPost,
    SocialReaction,
    User,
    UserFriend,
    UserFriendRequest,
    UserReward,
    VoiceClipCache,
    VoiceProfile,
    WatchHistory,
    WatchRoom,
    WatchRoomEvent,
    WatchRoomInvitation,
)
from .schemas import (
    ChatMessageCreate,
    DanmakuCreate,
    DanmakuReviewUpdate,
    EpisodeRemixCreate,
    EpisodeRemixReviewUpdate,
    EpisodeRemixVoiceCreate,
    ExperienceConfigUpdate,
    FriendCreate,
    InteractionCreate,
    LoginRequest,
    RegisterRequest,
    UserAdminUpdate,
    UserProfileUpdate,
    VoiceClipCreate,
    WatchHistoryUpdate,
    WatchRoomCreate,
    WatchRoomEventCreate,
    WatchRoomInvitationCreate,
    WatchRoomJoin,
    WatchRoomSync,
    SocialCommentCreate,
    SocialPostCreate,
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


VOICE_CONSENT_TEXT = "同意利用录入声音生成音频"
VOICE_ALLOWED_EXTENSIONS = {".wav", ".mp3", ".m4a", ".aac", ".ogg", ".webm"}
VOICE_MAX_BYTES = 12 * 1024 * 1024
VOICE_PROMPT_DIR = VOICE_ASSET_DIR / "prompts"
VOICE_CLIP_DIR = VOICE_ASSET_DIR / "clips"
SOCIAL_VOICE_ASSET_DIR = VOICE_ASSET_DIR / "social"
VOICE_TTS_SPEED = 1.18
VOICE_MODEL_VERSION = "cosyvoice-local-v2-fast"
REMIX_AUDIO_DIR = FRONTEND_DIR / "assets" / "remix_audio" / "beiwang_ep1"
ORIGINAL_REMIX_AUDIO_DIR = REMIX_AUDIO_DIR / "original"
AVATAR_ASSET_DIR = DATA_DIR / "avatar_assets"
AVATAR_POOL_DIR = ROOT_DIR / "avatars"
AVATAR_ALLOWED_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".gif"}
AVATAR_MAX_BYTES = 4 * 1024 * 1024
WHITE_PADDED_AVATAR_FILES = {
    "2f4b829648cd2f67926700df84e330.jpg",
    "2c5aeb0a197b7f6d97fcc8a390df63.jpg",
    "2bd80257b71cc283f6b22cd8bb8115.jpg",
    "1baa38604bad38f86b6fe6e38ae4e0.jpg",
}


def ensure_voice_dirs() -> None:
    VOICE_PROMPT_DIR.mkdir(parents=True, exist_ok=True)
    VOICE_CLIP_DIR.mkdir(parents=True, exist_ok=True)
    SOCIAL_VOICE_ASSET_DIR.mkdir(parents=True, exist_ok=True)
    REMIX_AUDIO_DIR.mkdir(parents=True, exist_ok=True)
    ORIGINAL_REMIX_AUDIO_DIR.mkdir(parents=True, exist_ok=True)


def ensure_avatar_dirs() -> None:
    AVATAR_ASSET_DIR.mkdir(parents=True, exist_ok=True)


def avatar_pool_files() -> list[Path]:
    if not AVATAR_POOL_DIR.exists():
        return []
    return sorted(
        path
        for path in AVATAR_POOL_DIR.iterdir()
        if path.is_file()
        and path.suffix.lower() in AVATAR_ALLOWED_EXTENSIONS
        and path.name not in WHITE_PADDED_AVATAR_FILES
    )


def avatar_pool_url_for_seed(seed: str) -> str:
    files = avatar_pool_files()
    if not files:
        return "preset:stage"
    digest = hashlib.sha256(seed.encode("utf-8")).hexdigest()
    index = int(digest[:8], 16) % len(files)
    return f"/media/avatar-pool/{files[index].name}"


def normalize_avatar_url(value: str | None) -> str:
    raw = (value or "").strip()
    if not raw:
        return ""
    if raw.startswith("preset:") and re.fullmatch(r"preset:[a-z0-9_-]{2,48}", raw):
        return raw
    if raw.startswith("/media/avatars/") and ".." not in raw and "\\" not in raw:
        return raw
    if raw.startswith("/media/avatar-pool/") and ".." not in raw and "\\" not in raw:
        filename = raw.removeprefix("/media/avatar-pool/")
        if filename and Path(filename).name == filename and (AVATAR_POOL_DIR / filename).is_file():
            return raw
    raise HTTPException(status_code=400, detail="头像地址仅支持系统预设或已上传头像")


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def normalize_voice_prompt_audio(source_path: Path, original_suffix: str) -> Path:
    target_path = source_path.with_name(f"{source_path.stem}_prompt.wav")
    if target_path.exists() and target_path.stat().st_mtime >= source_path.stat().st_mtime:
        return target_path
    try:
        subprocess.run(
            ["ffmpeg", "-y", "-i", str(source_path), "-ar", "16000", "-ac", "1", str(target_path)],
            capture_output=True,
            text=True,
            check=True,
            timeout=60,
        )
        return target_path
    except FileNotFoundError:
        if original_suffix == ".wav":
            return source_path
        raise HTTPException(status_code=500, detail="服务器缺少 ffmpeg，暂时无法处理浏览器录音")
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        if original_suffix == ".wav":
            return source_path
        raise HTTPException(status_code=400, detail="录音转码失败，请重新录制，或上传 wav 格式声音样本")


def voice_clip_payload(clip: VoiceClipCache, cached: bool = True) -> dict:
    return {
        "id": clip.id,
        "scene_key": clip.scene_key,
        "text": clip.text,
        "status": clip.status,
        "source": clip.source,
        "model_version": clip.model_version,
        "audio_url": clip.audio_url,
        "duration_sec": clip.duration_sec,
        "cached": cached,
        "created_at": clip.created_at.isoformat() if clip.created_at else None,
        "updated_at": clip.updated_at.isoformat() if clip.updated_at else None,
    }


def voice_profile_payload(profile: VoiceProfile | None, db: Session) -> dict:
    if not profile:
        return {
            "profile": None,
            "consent_text": VOICE_CONSENT_TEXT,
            "clips": [],
            "generation_ready": bool(COSYVOICE_BASE_URL),
        }
    clips = (
        db.query(VoiceClipCache)
        .filter(VoiceClipCache.voice_profile_id == profile.id)
        .order_by(VoiceClipCache.updated_at.desc(), VoiceClipCache.id.desc())
        .limit(8)
        .all()
    )
    return {
        "profile": {
            "id": profile.id,
            "status": profile.status,
            "source": profile.source,
            "prompt_text": profile.prompt_text,
            "prompt_audio_filename": profile.prompt_audio_filename,
            "created_at": profile.created_at.isoformat() if profile.created_at else None,
            "updated_at": profile.updated_at.isoformat() if profile.updated_at else None,
        },
        "consent_text": VOICE_CONSENT_TEXT,
        "clips": [voice_clip_payload(clip) for clip in clips],
        "generation_ready": bool(COSYVOICE_BASE_URL),
    }


def active_voice_profile(db: Session, user: User) -> VoiceProfile | None:
    return (
        db.query(VoiceProfile)
        .filter(VoiceProfile.user_id == user.id, VoiceProfile.status == "active")
        .order_by(VoiceProfile.updated_at.desc(), VoiceProfile.id.desc())
        .first()
    )


def post_multipart_json(
    url: str,
    fields: dict[str, str],
    file_field: str,
    file_path: Path,
    filename: str,
    content_type: str,
) -> dict:
    boundary = f"----short-drama-voice-{uuid4().hex}"
    chunks: list[bytes] = []
    for key, value in fields.items():
        chunks.extend(
            [
                f"--{boundary}".encode("utf-8"),
                f'Content-Disposition: form-data; name="{key}"'.encode("utf-8"),
                b"",
                str(value).encode("utf-8"),
            ]
        )
    chunks.extend(
        [
            f"--{boundary}".encode("utf-8"),
            (
                f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"'
            ).encode("utf-8"),
            f"Content-Type: {content_type}".encode("utf-8"),
            b"",
            file_path.read_bytes(),
            f"--{boundary}--".encode("utf-8"),
            b"",
        ]
    )
    body = b"\r\n".join(chunks)
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=COSYVOICE_TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


def download_voice_audio(provider_payload: dict, target_path: Path) -> None:
    provider_url = str(provider_payload.get("url") or "").strip()
    provider_path = Path(str(provider_payload.get("path") or ""))
    if provider_url:
        with urllib.request.urlopen(provider_url, timeout=COSYVOICE_TIMEOUT_SECONDS) as response:
            target_path.write_bytes(response.read())
        return
    if provider_path.exists():
        target_path.write_bytes(provider_path.read_bytes())
        return
    raise RuntimeError("CosyVoice did not return an audio file")


def copy_voice_audio(source_path: Path, target_path: Path) -> None:
    if target_path.suffix.lower() == ".mp3" and source_path.suffix.lower() != ".mp3":
        subprocess.run(
            ["ffmpeg", "-y", "-i", str(source_path), "-codec:a", "libmp3lame", "-q:a", "2", str(target_path)],
            capture_output=True,
            text=True,
            check=True,
            timeout=60,
        )
        return
    shutil.copyfile(source_path, target_path)


def normalize_tts_line(value: str) -> str:
    text = re.sub(r"[“”\"'‘’《》]", "", value or "")
    text = re.sub(r"\s+", "", text)
    text = re.sub(r"[，,、；;：:]+", "", text)
    text = re.sub(r"[。.!！?？…]+", "", text)
    return text.strip() or (value or "").strip()


def original_remix_voice_filename(variant_slot: str, shot_index: int, text: str) -> str:
    text_hash = sha256_text(normalize_tts_line(text))[:12]
    return f"{variant_slot}_shot_{shot_index}_{text_hash}.mp3"


def generate_voice_clip_file_with_gradio(text: str, prompt_text: str, prompt_wav_path: Path, output_path: Path) -> dict:
    try:
        from gradio_client import Client, handle_file
    except Exception as exc:
        raise RuntimeError("gradio_client is not installed") from exc

    client = Client(
        COSYVOICE_BASE_URL,
        verbose=False,
        httpx_kwargs={"timeout": max(5, COSYVOICE_TIMEOUT_SECONDS)},
    )
    result = client.predict(
        tts_text=normalize_tts_line(text),
        mode_checkbox_group="3s极速复刻",
        sft_dropdown="",
        prompt_text=normalize_tts_line(prompt_text),
        prompt_wav_upload=handle_file(str(prompt_wav_path)),
        prompt_wav_record=handle_file(str(prompt_wav_path)),
        instruct_text="",
        seed=0,
        stream=False,
        speed=VOICE_TTS_SPEED,
        api_name="/generate_audio",
    )
    if isinstance(result, (list, tuple)):
        result = result[0] if result else ""
    if isinstance(result, dict):
        result = result.get("path") or result.get("url") or ""
    result_text = str(result or "").strip()
    if not result_text:
        raise RuntimeError("CosyVoice Gradio did not return an audio file")
    if result_text.startswith("http://") or result_text.startswith("https://"):
        with urllib.request.urlopen(result_text, timeout=COSYVOICE_TIMEOUT_SECONDS) as response:
            output_path.write_bytes(response.read())
    else:
        source_path = Path(result_text)
        if not source_path.exists():
            raise RuntimeError("CosyVoice Gradio returned a missing audio file")
        copy_voice_audio(source_path, output_path)
    return {"path": str(output_path), "duration_seconds": 0, "source": "gradio_client"}


def generate_voice_clip_file_from_prompt(text: str, prompt_text: str, prompt_audio_path: Path, output_path: Path) -> dict:
    ensure_voice_dirs()
    prompt_suffix = prompt_audio_path.suffix.lower() or ".wav"
    prompt_wav_path = normalize_voice_prompt_audio(prompt_audio_path, prompt_suffix)
    tts_text = normalize_tts_line(text)
    prompt_tts_text = normalize_tts_line(prompt_text)
    try:
        payload = post_multipart_json(
            f"{COSYVOICE_BASE_URL}/api/tts/zero_shot",
            {
                "tts_text": tts_text,
                "prompt_text": prompt_tts_text,
                "seed": "0",
                "speed": f"{VOICE_TTS_SPEED:.2f}",
                "format": "mp3",
            },
            "prompt_wav",
            prompt_wav_path,
            prompt_wav_path.name,
            "audio/wav",
        )
        download_voice_audio(payload, output_path)
        return payload
    except Exception:
        return generate_voice_clip_file_with_gradio(tts_text, prompt_tts_text, prompt_wav_path, output_path)


def generate_voice_clip_file(profile: VoiceProfile, text: str, output_path: Path) -> dict:
    return generate_voice_clip_file_from_prompt(text, profile.prompt_text, Path(profile.prompt_audio_path), output_path)


def ensure_voice_clip(db: Session, user: User, profile: VoiceProfile, payload: VoiceClipCreate) -> dict:
    text = payload.text.strip()
    scene_key = payload.scene_key.strip()
    text_hash = sha256_text(text)
    cache_key = sha256_text(f"{VOICE_MODEL_VERSION}:{profile.id}:{scene_key}:{text_hash}")
    existing = db.query(VoiceClipCache).filter(VoiceClipCache.cache_key == cache_key).first()
    if existing and existing.status == "ready" and existing.audio_path and Path(existing.audio_path).exists():
        return voice_clip_payload(existing, cached=True)

    ensure_voice_dirs()
    clip = existing or VoiceClipCache(
        user_id=user.id,
        voice_profile_id=profile.id,
        cache_key=cache_key,
        scene_key=scene_key,
        text=text,
        text_hash=text_hash,
    )
    clip.status = "generating"
    clip.error_message = ""
    clip.updated_at = datetime.utcnow()
    if not existing:
        db.add(clip)
    db.commit()
    db.refresh(clip)

    filename = f"{uuid4().hex}.mp3"
    output_path = VOICE_CLIP_DIR / filename
    try:
        provider_payload = generate_voice_clip_file(profile, text, output_path)
        clip.status = "ready"
        clip.audio_path = str(output_path)
        clip.audio_url = f"/media/voice-clips/{filename}"
        clip.provider_url = str(provider_payload.get("url") or "")
        clip.duration_sec = float(provider_payload.get("duration_seconds") or 0)
        clip.source = "cosyvoice_zero_shot"
        clip.model_version = VOICE_MODEL_VERSION
        clip.updated_at = datetime.utcnow()
        db.commit()
        db.refresh(clip)
        return voice_clip_payload(clip, cached=False)
    except Exception:
        clip.status = "failed"
        clip.error_message = "voice generation failed"
        clip.updated_at = datetime.utcnow()
        db.commit()
        raise HTTPException(status_code=503, detail="语音生成服务暂时不可用，请确认本地 CosyVoice 服务已启动")


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


def quiz_reward_rules(episode: Episode | None) -> list[dict]:
    if not episode or not episode.experience_config:
        return []
    try:
        config = json.loads(episode.experience_config.config_json or "{}")
    except json.JSONDecodeError:
        return []
    rules = config.get("quiz_rewards") or config.get("prediction_rewards") or []
    return [rule for rule in rules if isinstance(rule, dict)]


def normalize_quiz_reward_rule(highlight: Highlight, rule: dict) -> dict | None:
    correct_option_key = str(rule.get("correct_option_key") or "").strip()
    if not correct_option_key:
        return None
    options = parse_options(highlight)
    correct_option = next((option for option in options if option.get("key") == correct_option_key), None)
    correct_label = str(rule.get("correct_label") or (correct_option.get("label") if correct_option else correct_option_key))
    reward_key = str(rule.get("reward_key") or f"quiz_{highlight.episode_id}_{highlight.id}_{correct_option_key}").strip()
    title = str(rule.get("title") or "剧情预言家").strip()
    description = str(rule.get("description") or f"猜中「{highlight.title}」的剧情选择。").strip()
    try:
        points = int(rule.get("points", 20))
    except (TypeError, ValueError):
        points = 20
    return {
        "kind": str(rule.get("kind") or "prediction"),
        "correct_option_key": correct_option_key,
        "correct_label": correct_label,
        "points": max(1, points),
        "reward_key": reward_key,
        "title": title,
        "description": description,
        "prompt": str(rule.get("prompt") or "同看竞猜题，答对后解锁称号和积分。"),
    }


def prediction_reward_rule(highlight: Highlight) -> dict | None:
    episode = highlight.episode
    for rule in quiz_reward_rules(episode):
        highlight_id = rule.get("highlight_id")
        if highlight_id is not None:
            try:
                matched_by_id = int(highlight_id) == int(highlight.id)
            except (TypeError, ValueError):
                matched_by_id = False
            if matched_by_id:
                return normalize_quiz_reward_rule(highlight, rule)
        if str(rule.get("highlight_title") or "").strip() == highlight.title:
            return normalize_quiz_reward_rule(highlight, rule)
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
            "description": reward_rule["prompt"],
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


def reward_collection_targets(db: Session, rewards: list[UserReward]) -> list[dict]:
    unlocked_by_base_key = {reward.reward_key.split(":", 1)[0]: reward for reward in rewards}
    targets = []
    seen_keys = set()
    rows = db.query(EpisodeExperienceConfig).join(Episode).order_by(Episode.id.asc()).all()
    for row in rows:
        episode = row.episode
        if not episode:
            continue
        for rule in quiz_reward_rules(episode):
            highlight = None
            highlight_id = rule.get("highlight_id")
            if highlight_id is not None:
                try:
                    highlight = db.get(Highlight, int(highlight_id))
                except (TypeError, ValueError):
                    highlight = None
            if not highlight:
                title = str(rule.get("highlight_title") or "").strip()
                highlight = (
                    db.query(Highlight)
                    .filter(Highlight.episode_id == episode.id, Highlight.title == title)
                    .first()
                    if title
                    else None
                )
            normalized = normalize_quiz_reward_rule(highlight, rule) if highlight else None
            reward_key = str((normalized or rule).get("reward_key") or "").strip()
            if not reward_key or reward_key in seen_keys:
                continue
            seen_keys.add(reward_key)
            unlocked = unlocked_by_base_key.get(reward_key)
            try:
                points = int((normalized or rule).get("points") or 20)
            except (TypeError, ValueError):
                points = 20
            targets.append(
                {
                    "reward_key": reward_key,
                    "title": str((normalized or rule).get("title") or "剧情预言家"),
                    "description": str((normalized or rule).get("description") or "完成本集竞猜后解锁。"),
                    "points": points,
                    "drama_title": episode.drama.title if episode.drama else "",
                    "episode_id": episode.id,
                    "episode_title": episode.title,
                    "episode_no": episode.episode_no,
                    "highlight_id": highlight.id if highlight else None,
                    "highlight_title": highlight.title if highlight else str(rule.get("highlight_title") or ""),
                    "unlocked": bool(unlocked),
                    "unlocked_at": unlocked.created_at.isoformat() if unlocked and unlocked.created_at else None,
                    "reward_id": unlocked.id if unlocked else None,
                }
            )
    return targets


def reward_profile(user: User, db: Session) -> dict:
    rewards = (
        db.query(UserReward)
        .filter(UserReward.user_id == user.id)
        .order_by(UserReward.created_at.desc(), UserReward.id.desc())
        .all()
    )
    points = sum(item.points or 0 for item in rewards)
    collection = reward_collection_targets(db, rewards)
    unlocked_count = sum(1 for item in collection if item["unlocked"])
    title = rewards[0].title if rewards else "剧情新人"
    if points >= 80:
        title = "高光收藏家"
    elif points >= 40:
        title = "剧情读心者"
    return {
        "points": points,
        "title": title,
        "badges": [reward_payload(item) for item in rewards],
        "collection": collection,
        "collection_total": len(collection),
        "collection_unlocked": unlocked_count,
        "completion_percent": round(unlocked_count * 100 / len(collection), 1) if collection else 0,
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


def safe_json_loads(raw: str | None, fallback):
    if not raw:
        return fallback
    try:
        return json.loads(raw)
    except Exception:
        return fallback


def danmaku_payload(comment: DanmakuComment, include_governance: bool = False) -> dict:
    payload = {
        "id": comment.id,
        "episode_id": comment.episode_id,
        "time_sec": comment.time_sec,
        "text": comment.text,
        "mode": comment.mode,
        "user": danmaku_user_payload(comment),
        "created_at": comment.created_at.isoformat() if comment.created_at else None,
    }
    if include_governance:
        payload.update(
            {
                "original_text": comment.original_text or comment.text,
                "source_like_count": comment.source_like_count or 0,
                "review_status": comment.review_status or "approved",
                "risk_score": round(float(comment.risk_score or 0), 3),
                "quality_score": round(float(comment.quality_score or 0), 3),
                "spoiler_score": round(float(comment.spoiler_score or 0), 3),
                "relevance_score": round(float(comment.relevance_score or 0), 3),
                "cluster_key": comment.cluster_key or "",
                "cluster_size": comment.cluster_size or 1,
                "suggested_time_sec": comment.suggested_time_sec,
                "moderation_model_version": comment.moderation_model_version or "",
                "moderation_reason": comment.moderation_reason or "",
                "moderation_layers": safe_json_loads(comment.moderation_layers_json, {}),
            }
        )
    return payload


def user_brief(user: User | None) -> dict | None:
    if not user:
        return None
    rewards = sorted(user.rewards, key=lambda item: (item.created_at or datetime.min, item.id or 0), reverse=True)
    points = sum(item.points or 0 for item in rewards)
    title = rewards[0].title if rewards else "剧情新人"
    if points >= 80:
        title = "高光收藏家"
    elif points >= 40:
        title = "剧情读心者"
    return {
        "id": user.id,
        "display_name": user.display_name,
        "avatar_url": user.avatar_url or "",
        "role": user.role,
        "growth_title": title,
        "points": points,
        "badge_count": len(rewards),
        "latest_badges": [reward_payload(item) for item in rewards[:3]],
    }


def are_friends(db: Session, user_id: int, friend_user_id: int) -> bool:
    return (
        db.query(UserFriend)
        .filter(
            UserFriend.user_id == user_id,
            UserFriend.friend_user_id == friend_user_id,
            UserFriend.status == "accepted",
        )
        .first()
        is not None
    )


def ensure_friend_edge(db: Session, user_id: int, friend_user_id: int) -> None:
    existing = (
        db.query(UserFriend)
        .filter(UserFriend.user_id == user_id, UserFriend.friend_user_id == friend_user_id)
        .first()
    )
    if existing:
        existing.status = "accepted"
        return
    db.add(UserFriend(user_id=user_id, friend_user_id=friend_user_id, status="accepted"))


def friend_payload(row: UserFriend) -> dict:
    return {"friendship_id": row.id, "status": row.status, "user": user_brief(row.friend)}


def friend_request_payload(row: UserFriendRequest, viewer: User) -> dict:
    return {
        "id": row.id,
        "status": row.status,
        "direction": "incoming" if row.to_user_id == viewer.id else "outgoing",
        "from_user": user_brief(row.from_user),
        "to_user": user_brief(row.to_user),
        "created_at": row.created_at.isoformat() if row.created_at else None,
        "responded_at": row.responded_at.isoformat() if row.responded_at else None,
    }


def latest_friend_request(db: Session, user_id: int, target_user_id: int) -> UserFriendRequest | None:
    return (
        db.query(UserFriendRequest)
        .filter(
            or_(
                and_(UserFriendRequest.from_user_id == user_id, UserFriendRequest.to_user_id == target_user_id),
                and_(UserFriendRequest.from_user_id == target_user_id, UserFriendRequest.to_user_id == user_id),
            )
        )
        .order_by(UserFriendRequest.created_at.desc(), UserFriendRequest.id.desc())
        .first()
    )


CHAT_MESSAGE_TYPES = {"text", "emoji", "watch_link", "clip_card"}


def chat_message_payload(message: ChatMessage, viewer: User) -> dict:
    return {
        "id": message.id,
        "from_user": user_brief(message.from_user),
        "to_user": user_brief(message.to_user),
        "direction": "outgoing" if message.from_user_id == viewer.id else "incoming",
        "message_type": message.message_type,
        "text": message.text,
        "payload": load_json_field(message.payload_json, {}),
        "is_read": bool(message.read_at),
        "created_at": message.created_at.isoformat() if message.created_at else None,
    }


def latest_chat_message(db: Session, user_id: int, friend_user_id: int) -> ChatMessage | None:
    return (
        db.query(ChatMessage)
        .filter(
            or_(
                and_(ChatMessage.from_user_id == user_id, ChatMessage.to_user_id == friend_user_id),
                and_(ChatMessage.from_user_id == friend_user_id, ChatMessage.to_user_id == user_id),
            )
        )
        .order_by(ChatMessage.created_at.desc(), ChatMessage.id.desc())
        .first()
    )


def unread_chat_count(db: Session, user_id: int, friend_user_id: int | None = None) -> int:
    query = db.query(ChatMessage).filter(ChatMessage.to_user_id == user_id, ChatMessage.read_at.is_(None))
    if friend_user_id is not None:
        query = query.filter(ChatMessage.from_user_id == friend_user_id)
    return query.count()


def chat_conversation_payload(row: UserFriend, viewer: User, db: Session) -> dict:
    last_message = latest_chat_message(db, viewer.id, row.friend_user_id)
    return {
        "friendship_id": row.id,
        "user": user_brief(row.friend),
        "unread_count": unread_chat_count(db, viewer.id, row.friend_user_id),
        "last_message": chat_message_payload(last_message, viewer) if last_message else None,
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


def invitation_payload(invitation: WatchRoomInvitation, viewer: User) -> dict:
    return {
        "id": invitation.id,
        "status": invitation.status,
        "room": room_payload(invitation.room, viewer),
        "from_user": user_brief(invitation.from_user),
        "to_user": user_brief(invitation.to_user),
        "created_at": invitation.created_at.isoformat() if invitation.created_at else None,
        "responded_at": invitation.responded_at.isoformat() if invitation.responded_at else None,
    }


SOCIAL_VISIBILITIES = {"public", "friends", "private"}
SOCIAL_SOURCE_TYPES = {"voice", "image", "story", "thought"}
SOCIAL_ASSET_KINDS = {"voice", "image", "story", "text"}
SOCIAL_FORBIDDEN_TERMS = ["淫秽", "低俗", "色情", "裸聊", "约炮", "做爱", "黄片", "成人视频"]
WINTER_VOICE_TOPIC_KEY = "winter_voice_match"
WINTER_VOICE_TOPIC_TITLE = "那年冬至主角模仿赛"
WINTER_VOICE_LINES = [
    {
        "key": "male_choice",
        "speaker": "男主",
        "role": "抉择",
        "time_sec": 110,
        "emotion": "压抑、犹豫、心动",
        "text": "我选第二个。不是因为容易，是因为我想留下来。",
        "hint": "前半句压低，后半句坚定，像终于说出了心里话。",
    },
    {
        "key": "female_close",
        "speaker": "女主",
        "role": "主动靠近",
        "time_sec": 50,
        "emotion": "试探、紧张、强装镇定",
        "text": "别动，我只是想确认你还会不会躲我。",
        "hint": "语速稍慢，尾音轻一点，保留一点暧昧和试探。",
    },
    {
        "key": "female_kiss",
        "speaker": "女主",
        "role": "心动爆发",
        "time_sec": 170,
        "emotion": "勇敢、甜、突然",
        "text": "这次换我靠近你，你不许再退了。",
        "hint": "声音要亮一些，像突然鼓起勇气，把心动说出口。",
    },
    {
        "key": "male_soft",
        "speaker": "男主",
        "role": "低声回应",
        "time_sec": 174,
        "emotion": "克制、心软、被打动",
        "text": "好，我不退。",
        "hint": "短句不要用力，低声但清楚，重点在停顿后的确定感。",
    },
]


def assert_social_text_safe(text: str) -> None:
    compact = re.sub(r"\s+", "", text or "").lower()
    for term in SOCIAL_FORBIDDEN_TERMS:
        if term.lower() in compact:
            raise HTTPException(status_code=400, detail="内容包含低俗或不适合公开交流的文字，请修改后再发布")


def social_topic_cards() -> list[dict]:
    return [
        {
            "key": "beiwang_voice",
            "title": "北往二创音卡",
            "subtitle": "把北往第一集片尾二创做成可听、可看、可分享的音卡",
            "tone": "road",
        },
        {
            "key": "winter_voice_match",
            "title": WINTER_VOICE_TOPIC_TITLE,
            "subtitle": "男主、女主高光台词都能挑战，听听谁最有冬至心动感",
            "tone": "winter",
        },
        {
            "key": "story_bottle",
            "title": "剧情漂流瓶",
            "subtitle": "公开扔出一张 AI 剧情卡，等陌生人接住评论",
            "tone": "story",
        },
    ]


def load_json_field(value: str | None, fallback):
    try:
        return json.loads(value or "")
    except (TypeError, json.JSONDecodeError):
        return fallback


def can_view_social_post(post: SocialPost, user: User, db: Session) -> bool:
    if post.user_id == user.id or post.visibility == "public":
        return True
    if post.visibility == "friends":
        return are_friends(db, user.id, post.user_id)
    return False


def ensure_social_post_visible(post: SocialPost, user: User, db: Session) -> None:
    if not can_view_social_post(post, user, db):
        raise HTTPException(status_code=404, detail="动态不存在或无权查看")


def social_comment_payload(comment: SocialComment) -> dict:
    return {
        "id": comment.id,
        "user": user_brief(comment.user),
        "text": "" if comment.is_deleted else comment.text,
        "is_deleted": comment.is_deleted,
        "created_at": comment.created_at.isoformat() if comment.created_at else None,
    }


def social_post_payload(post: SocialPost, viewer: User, db: Session, include_comments: bool = True) -> dict:
    comments_query = db.query(SocialComment).filter(SocialComment.post_id == post.id, SocialComment.is_deleted.is_(False))
    comments = comments_query.order_by(SocialComment.created_at.desc(), SocialComment.id.desc()).limit(3).all()
    liked_by_me = (
        db.query(SocialReaction)
        .filter(
            SocialReaction.post_id == post.id,
            SocialReaction.user_id == viewer.id,
            SocialReaction.reaction_type == "like",
        )
        .first()
        is not None
    )
    return {
        "id": post.id,
        "user": user_brief(post.user),
        "visibility": post.visibility,
        "source_type": post.source_type,
        "title": post.title,
        "text": post.text,
        "asset_kind": post.asset_kind,
        "asset_url": post.asset_url,
        "asset_payload": load_json_field(post.asset_payload_json, {}),
        "topic": post.topic,
        "like_count": db.query(SocialReaction).filter(SocialReaction.post_id == post.id).count(),
        "comment_count": comments_query.count(),
        "liked_by_me": liked_by_me,
        "can_delete_comments": post.user_id == viewer.id,
        "comments": [social_comment_payload(comment) for comment in reversed(comments)] if include_comments else [],
        "created_at": post.created_at.isoformat() if post.created_at else None,
    }


def notify_social_owner(db: Session, post: SocialPost, actor: User, event_type: str, comment_id: int | None = None) -> None:
    if post.user_id == actor.id:
        return
    db.add(
        SocialNotification(
            user_id=post.user_id,
            actor_user_id=actor.id,
            event_type=event_type,
            post_id=post.id,
            comment_id=comment_id,
        )
    )


def social_notification_payload(notification: SocialNotification) -> dict:
    return {
        "id": notification.id,
        "event_type": notification.event_type,
        "is_read": notification.is_read,
        "actor": user_brief(notification.actor),
        "post": {
            "id": notification.post.id,
            "title": notification.post.title,
            "source_type": notification.post.source_type,
        }
        if notification.post
        else None,
        "comment": social_comment_payload(notification.comment) if notification.comment else None,
        "created_at": notification.created_at.isoformat() if notification.created_at else None,
    }


def is_winter_solstice_first_episode(episode: Episode) -> bool:
    return "冬至" in (episode.drama.title or "") and int(episode.episode_no or 0) == 1


def winter_voice_activity_payload(episode: Episode, db: Session, user: User | None = None) -> dict:
    topic_posts = (
        db.query(SocialPost)
        .filter(SocialPost.topic == WINTER_VOICE_TOPIC_KEY, SocialPost.visibility == "public")
        .count()
    )
    return {
        "enabled": is_winter_solstice_first_episode(episode),
        "episode_id": episode.id,
        "drama_title": episode.drama.title,
        "episode_title": episode.title,
        "topic_key": WINTER_VOICE_TOPIC_KEY,
        "topic_title": WINTER_VOICE_TOPIC_TITLE,
        "trigger_time_sec": round(max(0, float(episode.duration_sec or 0) - 6), 2),
        "title": "片尾主角模仿赛",
        "subtitle": "选一句心动台词，自己录一版，或用你的声音生成一版，再分享到逛逛排行榜。",
        "lines": WINTER_VOICE_LINES,
        "post_count": topic_posts,
        "has_voice_profile": bool(user and active_voice_profile(db, user)),
    }


def social_topic_ranking(topic: str, viewer: User, db: Session, limit: int = 10) -> list[dict]:
    safe_limit = max(3, min(30, int(limit or 10)))
    rows = (
        db.query(SocialPost)
        .filter(SocialPost.topic == topic)
        .order_by(SocialPost.created_at.desc(), SocialPost.id.desc())
        .limit(120)
        .all()
    )
    visible = [post for post in rows if can_view_social_post(post, viewer, db)]
    visible.sort(
        key=lambda post: (
            db.query(SocialReaction).filter(SocialReaction.post_id == post.id).count(),
            db.query(SocialComment).filter(SocialComment.post_id == post.id, SocialComment.is_deleted.is_(False)).count(),
            post.created_at or datetime.min,
        ),
        reverse=True,
    )
    return [social_post_payload(post, viewer, db) for post in visible[:safe_limit]]


ROOM_EVENT_TYPES = {"danmaku", "danmaku_like", "danmaku_reply", "interaction"}


def public_room_event(event: WatchRoomEvent) -> dict:
    try:
        payload = json.loads(event.payload_json)
    except json.JSONDecodeError:
        payload = {}
    return {
        "id": event.id,
        "event_type": event.event_type,
        "payload": payload,
        "user": user_brief(event.user),
        "created_at": event.created_at.isoformat() if event.created_at else None,
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
        "quiz_rewards": [],
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


REMIX_SYSTEM_PROMPT = """
你是短剧片尾二创编剧。你只生成“AI 猜测/非正片”的文字剧情卡和三格分镜。

要求：
1. 只输出 JSON 对象，不要 Markdown。
2. 不要声称这是官方剧情。
3. 结合剧名、当前集高光和用户选择，生成适合移动端展示的短内容。
4. 北往第一集必须保留“欠薪、返乡、摩托、年三十回家”的现实质感。
5. storyboard 必须正好 3 个镜头，每个镜头包含 shot、visual、subtitle、sound。
6. 目标内容按 30 秒以内短片设计，不要写成长剧本。
7. 不要输出 API Key、接入点、环境变量或任何密钥内容。
""".strip()


BEIWANG_EP1_REMIX_VARIANTS = {
    "road_breakdown": [
        {
            "variant_key": "red_cola",
            "label": "红罐可乐救场",
            "variable_label": "饮料：红罐可乐",
            "summary": "摩托爆胎后，他用最后零钱买红罐可乐提神，借小卖部工具补胎继续赶路。",
            "runtime_sec": 26,
            "story_text": "摩托后轮在荒路上扎进铁钉，车身一沉，他差点把整个人的劲儿都泄掉。路边小卖部已经要关门，他用最后几枚零钱买了一罐红色可乐，冰得手心发麻。老板娘翻出旧补胎工具，他灌下一口甜的，像把那点委屈也咽了回去。补好胎后，他把空罐塞进包侧袋，重新扶起摩托：甜是甜，但家还远。",
            "storyboard": [
                {"shot": "镜头一", "visual": "荒路上旧摩托后轮爆胎，铁钉卡在轮胎上，两人停在路边。", "subtitle": "“还有几十公里，不能在这儿停。”", "sound": "轮胎漏气声"},
                {"shot": "镜头二", "visual": "乡镇小卖部半关门，主角握着无字红罐可乐，工友拿着旧补胎工具。", "subtitle": "“喝口甜的，手就不抖了。”", "sound": "开罐气泡声"},
                {"shot": "镜头三", "visual": "路灯下轮胎补好，红罐可乐塞在包侧袋，两人重新扶起摩托。", "subtitle": "“甜是甜，路还得自己骑。”", "sound": "摩托点火声"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "荒路爆胎", "video_prompt": "东北冬日荒路，旧摩托后轮爆胎，铁钉明显卡在轮胎上，两位返乡青年停在路边查看，车尾绑着行李，写实竖屏短剧", "sound": "漏气、寒风"},
                {"duration_sec": 9, "caption": "买红罐可乐", "video_prompt": "暖黄色乡镇小卖部半关门，蓝灰外套青年握着无字红色汽水罐，手被冻红，格子衬衫工友拿着旧补胎工具，红罐清晰可见，无商标文字", "sound": "开罐气泡"},
                {"duration_sec": 9, "caption": "补好继续走", "video_prompt": "路灯下旧摩托轮胎补好，红色汽水罐插在行李包侧袋，两位青年扶起摩托准备继续上路，冬夜道路延伸，现实温暖", "sound": "摩托点火"},
            ],
        },
        {
            "variant_key": "green_soda",
            "label": "绿罐汽水提神",
            "variable_label": "饮料：绿罐汽水",
            "summary": "后轮爆胎时，他买绿罐汽水给自己壮胆，靠一点冲劲把摩托重新修好。",
            "runtime_sec": 27,
            "story_text": "铁钉扎破后轮时，他第一反应不是疼，是烦。小卖部老板说补胎工具旧得不一定好使，他却从冰柜里拿出一罐绿色汽水，说越冷越得来点冲的。汽水开罐的一声像给自己壮胆，气泡顶上来，他也跟着缓过来。补胎时手冻得直抖，嘴上还贫：这玩意儿比我摩托还带劲。等后轮重新鼓起来，他把易拉罐压扁，继续往北骑。",
            "storyboard": [
                {"shot": "镜头一", "visual": "孤路上摩托后轮泄气，主角指着轮胎露出又气又好笑的表情。", "subtitle": "“不是，连轮胎都想过年歇着？”", "sound": "轮胎漏气声"},
                {"shot": "镜头二", "visual": "小卖部门口，主角打开无字绿罐汽水，气泡从罐口涌起。", "subtitle": "“越苦，越得来点冲的。”", "sound": "汽水气泡声"},
                {"shot": "镜头三", "visual": "主角蹲在补好的轮胎旁，把绿罐放在摩托座边，对着摩托开玩笑。", "subtitle": "“行，咱俩都别泄气。”", "sound": "轻笑和发动机低鸣"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "后轮泄气", "video_prompt": "孤独冬日公路，旧摩托后轮变瘪，蓝灰外套青年指着轮胎露出苦笑，格子衬衫工友蹲下检查铁钉，写实短剧但带一点荒诞喜感", "sound": "漏气声"},
                {"duration_sec": 9, "caption": "买绿罐汽水", "video_prompt": "小卖部门口暖光，青年打开无字绿色柠檬汽水罐，清晰气泡从罐口涌起，工友拿着补胎工具看着他笑，冷空气和暖灯对比，绿色罐子必须明显", "sound": "开罐气泡"},
                {"duration_sec": 10, "caption": "气泡和发动机", "video_prompt": "旧摩托轮胎补好，青年把无字绿色汽水罐放在摩托座旁，一边拧气门芯一边开玩笑，工友绑紧行李，白色呼吸雾，路灯柔光", "sound": "轻笑、发动机低鸣"},
            ],
        },
        {
            "variant_key": "mineral_water",
            "label": "矿泉水硬撑",
            "variable_label": "饮料：矿泉水",
            "summary": "他没钱买更贵的东西，只买矿泉水补口气，克制地把摩托修好继续赶年三十。",
            "runtime_sec": 25,
            "story_text": "后轮泄气时，他先数了数兜里的钱，够买最便宜的一瓶水，也够买一卷胶条。他没说丧气话，只拧开无字矿泉水，喝了一口，像给自己按了暂停。小卖部老板把旧打气筒递出来，他蹲在路边一点点补胎，动作慢，但没停。水瓶被他塞进行李缝里，摩托重新站稳，他也重新站稳。",
            "storyboard": [
                {"shot": "镜头一", "visual": "荒路旁主角站在爆胎摩托边，低头数掌心零钱。", "subtitle": "“不矫情，补口水继续赶路。”", "sound": "风吹硬币声"},
                {"shot": "镜头二", "visual": "主角在小卖部门口喝无字透明矿泉水，工友接过旧打气筒。", "subtitle": "“便宜点，能顶事。”", "sound": "拧瓶盖声"},
                {"shot": "镜头三", "visual": "摩托轮胎重新鼓起，透明水瓶塞在行李缝里，两人准备继续骑。", "subtitle": "“不快，但不停。”", "sound": "打气筒最后一下"},
            ],
            "video_shots": [
                {"duration_sec": 7, "caption": "数零钱", "video_prompt": "荒凉冬日公路旁，旧摩托爆胎，蓝灰外套青年站在车边数掌心几枚零钱，格子衬衫工友检查轮胎和行李，情绪克制现实", "sound": "风声、硬币声"},
                {"duration_sec": 9, "caption": "买矿泉水", "video_prompt": "小卖部门口，青年喝一瓶透明无标签矿泉水，工友从老板手里接过旧打气筒和补胎胶条，画面安静克制，透明水瓶必须清晰，无文字无商标", "sound": "瓶盖声"},
                {"duration_sec": 9, "caption": "安静上路", "video_prompt": "路边旧摩托后轮重新鼓起，透明矿泉水瓶塞在行李缝里，两个青年扶车准备继续骑，长路延伸，现实主义短剧质感", "sound": "打气筒、摩托低鸣"},
            ],
        },
    ],
    "ticket_home": [
        {
            "variant_key": "green_train",
            "label": "借钱买绿皮火车票",
            "variable_label": "票种：绿皮火车",
            "summary": "骑车太苦让他动摇，遇到返乡父女后决定借钱买一张绿皮火车票，先把人带回家。",
            "runtime_sec": 28,
            "story_text": "连续几小时寒风钻进棉衣，主角第一次冒出念头：要不别骑了。他在候车亭看见一对父女，孩子抱着冻硬的饺子盒问爸爸能不能赶上年夜饭。那句话像针一样扎到他。他给工友发去语音，别的不用，先借我一张票钱。夜里，他攥着绿皮火车票挤上车，摩托寄存在小站，回家这件事换了一种方式继续。",
            "storyboard": [
                {"shot": "镜头一", "visual": "寒风里主角停在候车亭，脸被吹得发僵。", "subtitle": "“我是不是扛不住了？”", "sound": "风吹铁皮棚"},
                {"shot": "镜头二", "visual": "小女孩抱着饺子盒，问父亲能不能赶上年夜饭。", "subtitle": "“爸爸，奶奶会等我们吗？”", "sound": "孩子低声问话"},
                {"shot": "镜头三", "visual": "绿皮火车慢慢启动，主角站在车厢连接处看向窗外。", "subtitle": "“车不骑了，家还得回。”", "sound": "火车鸣笛"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "候车亭动摇", "video_prompt": "东北乡镇候车亭，寒风吹铁皮棚，打工返乡青年抱着头盔坐下，旧摩托停在旁边，表情疲惫动摇，写实竖屏", "sound": "风声"},
                {"duration_sec": 9, "caption": "孩子一句话", "video_prompt": "候车亭内一对返乡父女，小女孩抱着饺子盒问能否赶上年夜饭，青年在旁边听见后抬头，温暖现实主义", "sound": "孩子声音"},
                {"duration_sec": 11, "caption": "绿皮火车", "video_prompt": "夜色小站，绿皮火车启动，青年攥着车票站在车厢连接处，窗外雪地倒退，旧摩托寄存在站外，返乡继续", "sound": "火车鸣笛"},
            ],
        },
        {
            "variant_key": "coach_ticket",
            "label": "借钱买大巴票",
            "variable_label": "票种：长途大巴",
            "summary": "骑车到体力崩溃，他听见母亲的语音后向工友借钱，买最后一张长途大巴票回家。",
            "runtime_sec": 26,
            "story_text": "路边服务区的热水机坏了，主角坐在台阶上，手指冻得握不住钥匙。他本来想硬撑，直到母亲又发来一条语音：别逞能，平安到就行。那一刻他低下头，给工友打电话，声音小得像认输：哥，借我点票钱。最后一班大巴只剩一张后排票，他把摩托托管在服务区，抱着行李上车，第一次允许自己不逞强。",
            "storyboard": [
                {"shot": "镜头一", "visual": "服务区台阶，主角握不住摩托钥匙，热水机贴着故障纸。", "subtitle": "“再骑下去，真回不去了。”", "sound": "空旷服务区提示音"},
                {"shot": "镜头二", "visual": "手机屏幕亮起母亲语音，他听完后拨给工友。", "subtitle": "“哥，借我点票钱。”", "sound": "电话接通声"},
                {"shot": "镜头三", "visual": "长途大巴车尾灯亮起，主角坐在最后一排抱紧行李。", "subtitle": "“不逞强，也算往家走。”", "sound": "大巴发动声"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "服务区崩溃", "video_prompt": "冬夜服务区台阶，热水机故障，青年握不住摩托钥匙，旧摩托停在一旁，身体疲惫，现实主义短剧", "sound": "服务区空响"},
                {"duration_sec": 8, "caption": "借票钱", "video_prompt": "手机母亲语音亮起，青年低头拨电话向工友借钱，表情羞愧又坚定，冷色环境中有一点手机暖光", "sound": "电话接通"},
                {"duration_sec": 10, "caption": "上大巴", "video_prompt": "最后一班长途大巴夜里发车，青年抱行李坐在后排，窗外服务区和旧摩托渐远，返乡现实感", "sound": "大巴发动"},
            ],
        },
        {
            "variant_key": "high_speed_standing",
            "label": "借钱买高铁站票",
            "variable_label": "票种：高铁站票",
            "summary": "暴雪封路让骑车变危险，他借钱抢到高铁站票，站着也要继续赶年三十。",
            "runtime_sec": 27,
            "story_text": "暴雪预警一响，路口的交警把摩托全拦了下来。主角看着手机上跳出的红色封路提示，嘴硬了半天，最后还是给工友发了一句：高铁站票也行，帮我抢一张。高铁站里人潮往检票口挤，他背着行李站在候车大厅边上，鞋边还沾着雪泥。有人说站几个小时太遭罪，他却笑了一下：站着也行，站着说明我还在往家赶。",
            "storyboard": [
                {"shot": "镜头一", "visual": "路口警示灯闪烁，交警拦下摩托，暴雪压低视线。", "subtitle": "“前面封了，不能骑。”", "sound": "警示喇叭"},
                {"shot": "镜头二", "visual": "主角在高铁站角落盯着手机抢票页面，最后点下确认。", "subtitle": "“站着也行，能回去就行。”", "sound": "抢票提示音"},
                {"shot": "镜头三", "visual": "高铁车厢连接处人很多，主角背着行李站稳，鞋边雪泥慢慢化开。", "subtitle": "“站着也算往家赶。”", "sound": "车厢人声"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "暴雪封路", "video_prompt": "东北公路暴雪，路口警示灯和交警拦下旧摩托，青年披着雪站在车旁，返乡路被迫中断，竖屏写实", "sound": "警示喇叭"},
                {"duration_sec": 8, "caption": "抢高铁站票", "video_prompt": "青年在现代高铁站角落盯着手机抢票页面，屏幕不可读，手指犹豫后点下确认，周围人群焦急，写实竖屏", "sound": "手机提示"},
                {"duration_sec": 11, "caption": "站上高铁", "video_prompt": "现代高铁车厢连接处，青年背着行李站稳，鞋边雪泥融化，车窗外夜色飞过，他露出释然笑意", "sound": "车厢人声"},
            ],
        },
    ],
    "kindness_ride": [
        {
            "variant_key": "wuling_van",
            "label": "五菱面包车顺路",
            "variable_label": "车主：五菱面包车",
            "summary": "他帮面包车车主推车，回头发现摩托被偷，车主也是东北人，直接载他一起回家。",
            "runtime_sec": 28,
            "story_text": "路边一辆五菱面包车陷进雪沟，车主急得满头汗，后座还坐着抱保温桶的母亲。主角二话没说下车帮推，鞋全湿了。等车出来，他回头一看，自己的摩托不见了，只剩地上一道拖痕。他愣住时，车主听见他的东北口音，拍了拍副驾：兄弟，上车吧，我也往北走。摩托丢了，回家的路却多了个人情味。",
            "storyboard": [
                {"shot": "镜头一", "visual": "五菱面包车陷在雪沟，主角下车帮忙推车。", "subtitle": "“搭把手，先把人弄出来。”", "sound": "轮胎打滑声"},
                {"shot": "镜头二", "visual": "他回头发现摩托不见，只剩雪地拖痕。", "subtitle": "“我车呢？”", "sound": "风声骤停"},
                {"shot": "镜头三", "visual": "车主打开副驾门，车里暖光照到他的脸。", "subtitle": "“上车，都是往东北回。”", "sound": "车门打开声"},
            ],
            "video_shots": [
                {"duration_sec": 9, "caption": "帮推五菱", "video_prompt": "雪夜路边，五菱面包车陷进雪沟，返乡青年下车帮车主推车，后座母亲抱保温桶，现实主义温暖，竖屏", "sound": "轮胎打滑"},
                {"duration_sec": 8, "caption": "摩托被偷", "video_prompt": "青年推完车回头，旧摩托不见，只剩雪地拖痕和掉落的绑绳，表情从热心变成懵住，短剧反转", "sound": "风声骤停"},
                {"duration_sec": 11, "caption": "顺路回家", "video_prompt": "五菱车主打开副驾门，车内暖光照出东北地图和年货，青年抱行李上车，一起往北方夜路开去", "sound": "车门和发动机"},
            ],
        },
        {
            "variant_key": "audi_sedan",
            "label": "奥迪车主顺路",
            "variable_label": "车主：奥迪轿车",
            "summary": "他帮奥迪车主换胎送老人，摩托却被偷，车主被他的善意打动，顺路送他回东北。",
            "runtime_sec": 27,
            "story_text": "一辆奥迪停在路肩，车主急着送老人去前面镇医院，却不会换备胎。主角本来赶时间，还是把摩托停下，脱了手套帮他拧螺丝。老人上车前塞给他一把热糖。等他回到路边，摩托已经被人推走。车主沉默几秒，把后备箱重新打开：兄弟，你帮我一程，我送你一程。雪夜里，副驾的安全带替他接住了那口委屈。",
            "storyboard": [
                {"shot": "镜头一", "visual": "奥迪停在路肩，老人裹着围巾，主角弯腰换备胎。", "subtitle": "“先救急，别耽误老人。”", "sound": "扳手拧动"},
                {"shot": "镜头二", "visual": "老人塞给他热糖，他再回头时摩托已经不见。", "subtitle": "“这也太背了吧。”", "sound": "糖纸声后突然安静"},
                {"shot": "镜头三", "visual": "车主打开副驾门，主角把行李放进后备箱。", "subtitle": "“你帮我一程，我送你一程。”", "sound": "安全带扣上"},
            ],
            "video_shots": [
                {"duration_sec": 9, "caption": "路肩换胎", "video_prompt": "雪夜高速辅路，一辆奥迪轿车停在路肩，返乡青年蹲下帮车主换备胎，老人裹围巾坐在车内，现实质感", "sound": "扳手声"},
                {"duration_sec": 8, "caption": "摩托消失", "video_prompt": "老人递给青年热糖，青年回头发现旧摩托消失，只剩绑绳和车轮印，奥迪车主表情愧疚，短剧反转", "sound": "糖纸、静默"},
                {"duration_sec": 10, "caption": "副驾回家", "video_prompt": "奥迪后备箱装入返乡行李，青年坐上副驾系安全带，车内暖光，车窗外雪夜公路向东北延伸", "sound": "安全带扣上"},
            ],
        },
        {
            "variant_key": "range_rover_suv",
            "label": "揽胜越野雪路救援",
            "variable_label": "车主：揽胜越野",
            "summary": "他帮揽胜越野车主脱困，摩托被偷后，对方腾出后备箱和座位载他往东北走。",
            "runtime_sec": 25,
            "story_text": "雪越下越厚，一辆深色揽胜越野陷在村外坡道，车主急着把药送回家。主角看了眼时间，还是停下帮他垫木板、推车。车轮终于咬住雪面冲出来，他却发现自己的摩托被人顺走了。车主没说漂亮话，只把后排座椅放倒，腾出一块位置：东西放上来，我车能走雪路，先送你回东北。那一刻，他第一次觉得这条路没那么孤单。",
            "storyboard": [
                {"shot": "镜头一", "visual": "深色揽胜越野陷在雪坡，主角和工友垫木板推车。", "subtitle": "“雪路难走，先帮他出来。”", "sound": "轮胎碾雪声"},
                {"shot": "镜头二", "visual": "越野车脱困后，主角回头发现摩托没了。", "subtitle": "“我摩托呢？”", "sound": "风声骤停"},
                {"shot": "镜头三", "visual": "越野车后门打开，后排放倒，主角把行李塞进去。", "subtitle": "“东西放上来，我送你往北走。”", "sound": "后备箱打开声"},
            ],
            "video_shots": [
                {"duration_sec": 7, "caption": "越野陷坡", "video_prompt": "雪夜村外坡道，深色揽胜风格越野车陷在雪里，车主拿着药袋焦急，返乡青年停下帮忙垫木板推车", "sound": "轮胎碾雪"},
                {"duration_sec": 8, "caption": "摩托被顺走", "video_prompt": "越野车刚脱困，青年回头发现旧摩托消失，只剩雪地拖痕和松开的绑绳，深色豪华越野车车灯照亮空位", "sound": "风声骤停"},
                {"duration_sec": 10, "caption": "越野送回家", "video_prompt": "深色揽胜风格越野车后门打开，后排放倒，青年和工友把返乡行李放进去，车灯穿过雪夜往北走", "sound": "后备箱和发动机"},
            ],
        },
    ],
}


def is_beiwang_first_episode(episode: Episode) -> bool:
    return bool(episode.drama and "北往" in episode.drama.title and int(episode.episode_no or 0) == 1)


def public_remix_variant(variant: dict | None) -> dict | None:
    if not variant:
        return None
    return {
        "variant_key": variant["variant_key"],
        "label": variant["label"],
        "variable_label": variant["variable_label"],
        "summary": variant["summary"],
        "runtime_sec": variant["runtime_sec"],
    }


def remix_variant_for_choice(choice: dict, session_id: str, variant_key: str | None = None) -> dict | None:
    variants = BEIWANG_EP1_REMIX_VARIANTS.get(choice["key"], [])
    if not variants:
        return None
    if variant_key:
        return next((item for item in variants if item["variant_key"] == variant_key), None)
    digest = hashlib.sha1(f"{session_id}:{choice['key']}".encode("utf-8")).hexdigest()
    return variants[int(digest[:8], 16) % len(variants)]


def remix_video_plan(choice: dict, variant: dict | None) -> dict | None:
    if not variant:
        return None
    slot = f"beiwang_ep1_{choice['key']}_{variant['variant_key']}"
    storage_hint = f"/assets/remix_videos/beiwang_ep1/{slot}.mp4"
    asset_path = FRONTEND_DIR / storage_hint.lstrip("/")
    return {
        "asset_status": "cached_video" if asset_path.exists() else "script_ready",
        "render_mode": "pre_generated_video",
        "target_duration_sec": variant["runtime_sec"],
        "variant_slot": slot,
        "replacement_axis": variant["variable_label"],
        "storage_hint": storage_hint,
        "note": "已按该视频提示词预生成并缓存，客户端按用户 session 分配不同版本。",
        "shots": variant["video_shots"],
    }


def remix_image_plan(choice: dict, variant: dict | None) -> dict | None:
    if not variant:
        return None
    slot = f"beiwang_ep1_{choice['key']}_{variant['variant_key']}"
    shots = []
    for index, shot in enumerate(variant.get("video_shots") or [], start=1):
        storage_hint = f"/assets/remix_images/beiwang_ep1/{slot}_shot_{index}.png"
        asset_path = FRONTEND_DIR / storage_hint.lstrip("/")
        storyboard = (variant.get("storyboard") or [{}])[index - 1] if index <= len(variant.get("storyboard") or []) else {}
        prompt = shot.get("video_prompt") or storyboard.get("visual") or variant.get("summary") or ""
        audio_text = storyboard.get("subtitle") or shot.get("caption") or ""
        audio_filename = original_remix_voice_filename(slot, index, audio_text)
        audio_storage_hint = f"/assets/remix_audio/beiwang_ep1/original/{audio_filename}"
        audio_path = ORIGINAL_REMIX_AUDIO_DIR / audio_filename
        shots.append(
            {
                "index": index,
                "caption": shot.get("caption") or storyboard.get("shot") or f"镜头{index}",
                "subtitle": storyboard.get("subtitle") or "",
                "sound": storyboard.get("sound") or shot.get("sound") or "",
                "image_prompt": prompt,
                "storage_hint": storage_hint,
                "asset_status": "cached_image" if asset_path.exists() else "script_ready",
                "audio_text": audio_text,
                "audio_storage_hint": audio_storage_hint,
                "audio_status": "ready" if audio_path.exists() else "pending_upload",
            }
        )
    all_cached = bool(shots) and all(item["asset_status"] == "cached_image" for item in shots)
    return {
        "asset_status": "cached_images" if all_cached else "script_ready",
        "render_mode": "click_through_storyboard_images",
        "generation_model": "gpt-image-1.5",
        "variant_slot": slot,
        "choice_key": choice["key"],
        "variant_key": variant["variant_key"],
        "replacement_axis": variant["variable_label"],
        "shot_count": len(shots),
        "storage_dir": "/assets/remix_images/beiwang_ep1",
        "note": "当前片尾二创改为三镜头图片分镜。可先用缓存图展示，后续用 OpenAI 图片生成脚本覆盖同名图片。",
        "shots": shots,
    }


def ensure_original_remix_voice_clip(variant_slot: str, shot_index: int, text: str) -> dict:
    prompt_path = VOICE_PROMPT_DIR / "system" / "beiwang_ep1_main_prompt.wav"
    if not prompt_path.exists():
        raise HTTPException(status_code=503, detail="缺少北往原版参考音频，请先截取主角台词样本")
    filename = original_remix_voice_filename(variant_slot, shot_index, text)
    output_path = ORIGINAL_REMIX_AUDIO_DIR / filename
    audio_url = f"/assets/remix_audio/beiwang_ep1/original/{filename}"
    if output_path.exists():
        return {
            "voice_mode": "original",
            "scene_key": f"{variant_slot}_shot_{shot_index}",
            "text": text,
            "status": "ready",
            "source": "cosyvoice_original_prompt",
            "model_version": VOICE_MODEL_VERSION,
            "audio_url": audio_url,
            "duration_sec": 0,
            "cached": True,
        }
    try:
        provider_payload = generate_voice_clip_file_from_prompt(text, "你这玩意叫摇滚啊", prompt_path, output_path)
    except Exception:
        raise HTTPException(status_code=503, detail="原版声音生成暂时不可用，请确认本地 CosyVoice 服务已启动")
    return {
        "voice_mode": "original",
        "scene_key": f"{variant_slot}_shot_{shot_index}",
        "text": text,
        "status": "ready",
        "source": "cosyvoice_original_prompt",
        "model_version": VOICE_MODEL_VERSION,
        "audio_url": audio_url,
        "duration_sec": float(provider_payload.get("duration_seconds") or 0),
        "cached": False,
    }


def remix_options_for_episode(episode: Episode) -> list[dict]:
    title = episode.drama.title
    if is_beiwang_first_episode(episode):
        return [
            {
                "key": "road_breakdown",
                "label": "车坏在半路",
                "description": "摩托半路爆胎，他会买红罐可乐、绿罐汽水还是矿泉水，撑住继续回家。",
                "tone": "返乡闯关",
                "icon": "修",
                "variant_count": 3,
                "target_duration_sec": 28,
                "variants": [public_remix_variant(item) for item in BEIWANG_EP1_REMIX_VARIANTS["road_breakdown"]],
            },
            {
                "key": "ticket_home",
                "label": "借钱买票回家",
                "description": "骑车太苦让他动摇，路上遇到一个具体事件后，他放下逞强，借钱买票继续回家。",
                "tone": "现实共情",
                "icon": "票",
                "variant_count": 3,
                "target_duration_sec": 28,
                "variants": [public_remix_variant(item) for item in BEIWANG_EP1_REMIX_VARIANTS["ticket_home"]],
            },
            {
                "key": "kindness_ride",
                "label": "帮人后一起回家",
                "description": "他路上帮了别人，摩托却被偷；被帮助的人正好也往东北走，最后载他一起回家。",
                "tone": "善意反转",
                "icon": "善",
                "variant_count": 3,
                "target_duration_sec": 28,
                "variants": [public_remix_variant(item) for item in BEIWANG_EP1_REMIX_VARIANTS["kindness_ride"]],
            },
        ]
    if "冬至" in title:
        return [
            {
                "key": "truth_confess",
                "label": "男主说出真相",
                "description": "他没有继续逃避，把最不敢说的秘密说出口。",
                "tone": "爱情坦白",
                "icon": "真",
            },
            {
                "key": "snow_escape",
                "label": "雪夜短暂逃离",
                "description": "两个人离开压抑的房间，在冬夜里完成一个小小约定。",
                "tone": "甜虐治愈",
                "icon": "雪",
            },
        ]
    if "寻宝" in title or "北派" in title:
        return [
            {
                "key": "map_secret",
                "label": "地图背面显字",
                "description": "藏宝图被火光一烤，背面露出真正路线。",
                "tone": "机关揭秘",
                "icon": "图",
            },
            {
                "key": "guardian_choice",
                "label": "守宝人现身",
                "description": "门后的人没有动手，只问主角敢不敢继续往下走。",
                "tone": "冒险悬念",
                "icon": "门",
            },
        ]
    if "云渺" in title or "仙" in title:
        return [
            {
                "key": "visitor_identity",
                "label": "访客身份反转",
                "description": "来人并不是敌人，而是被抹去记忆的旧识。",
                "tone": "仙侠反转",
                "icon": "灵",
            },
            {
                "key": "power_reveal",
                "label": "隐藏力量觉醒",
                "description": "危机逼近时，主角身上的封印突然裂开。",
                "tone": "高能觉醒",
                "icon": "阵",
            },
        ]
    return [
        {
            "key": "reverse_next",
            "label": "身份反转",
            "description": "最后一幕里的细节被重新解释，人物关系突然变了。",
            "tone": "剧情反转",
            "icon": "反",
        },
        {
            "key": "warm_next",
            "label": "情绪和解",
            "description": "冲突暂时停下，角色做出一个出乎意料的温柔选择。",
            "tone": "情绪延展",
            "icon": "暖",
        },
    ]


def remix_context_payload(episode: Episode, db: Session) -> dict:
    highlights = (
        db.query(Highlight)
        .filter(Highlight.episode_id == episode.id)
        .order_by(Highlight.start_time_sec.asc())
        .all()
    )
    return {
        "episode_id": episode.id,
        "drama_title": episode.drama.title,
        "episode_title": episode.title,
        "genre": episode.drama.genre,
        "duration_sec": episode.duration_sec,
        "highlights": [
            {
                "time": item.start_time_sec,
                "title": item.title,
                "type": item.highlight_type,
                "emotion": item.emotion,
                "description": item.description,
                "evidence_text": item.evidence_text,
            }
            for item in highlights[-4:]
        ],
    }


def fallback_remix_payload(episode: Episode, choice: dict, context: dict, variant: dict | None = None) -> dict:
    drama_title = episode.drama.title
    highlights = context.get("highlights") or []
    last_highlight = highlights[-1] if highlights else {}
    scene_seed = last_highlight.get("description") or last_highlight.get("title") or "片尾留下悬念"
    title = f"{choice['label']} · AI 猜测卡"
    logline = f"如果片尾继续往下演，{choice['description']}"
    if "冬至" in drama_title:
        story_text = (
            f"雪夜的安静把两个人的心事放大。上一秒还没说出口的话，在片尾停顿里变成新的选择："
            f"{choice['description']} 这个走向会把甜和疼放在同一个镜头里，让观众继续猜他到底敢不敢面对她。"
        )
        storyboard = [
            {"shot": "镜头一", "visual": "冷色走廊，灯光只照到两人的手。", "subtitle": "“你真的想听实话吗？”", "sound": "低频心跳声"},
            {"shot": "镜头二", "visual": "窗外飘雪，女主回头，表情从强撑变成动摇。", "subtitle": choice["description"], "sound": "雪声和轻微呼吸"},
            {"shot": "镜头三", "visual": "近景定格在两人靠近的距离，画面淡出。", "subtitle": "下一集：别再躲了。", "sound": "钢琴尾音"},
        ]
    elif "北往" in drama_title and variant:
        title = f"{choice['label']} · {variant['label']}"
        logline = variant["summary"]
        story_text = variant["story_text"]
        storyboard = variant["storyboard"]
    elif "北往" in drama_title:
        story_text = (
            f"摩托的轰鸣停下来，返乡路却没有真正结束。片尾可以顺着“{scene_seed}”继续推进："
            f"{choice['description']} 这个猜测保留了北往的现实感，也给下一集留下更强的回家悬念。"
        )
        storyboard = [
            {"shot": "镜头一", "visual": "摩托车灯扫过村口土路，远处有一盏家里的灯。", "subtitle": "“终于到了。”", "sound": "发动机熄火"},
            {"shot": "镜头二", "visual": "主角停住脚步，手机或门口细节突然进入特写。", "subtitle": choice["description"], "sound": "电话震动/风声"},
            {"shot": "镜头三", "visual": "他看向家门，脸上的轻松慢慢收住。", "subtitle": "下一集：回家，比上路更难。", "sound": "鼓点切断"},
        ]
    else:
        story_text = (
            f"片尾停在情绪最高的位置，AI 按用户选择延展为：{choice['description']} "
            f"这个版本围绕“{scene_seed}”继续制造下一集钩子。"
        )
        storyboard = [
            {"shot": "镜头一", "visual": "片尾定格画面被重新拉近，角色注意到一个细节。", "subtitle": "“等一下。”", "sound": "环境声降低"},
            {"shot": "镜头二", "visual": "关键物件或眼神特写，暗示新冲突。", "subtitle": choice["description"], "sound": "短促提示音"},
            {"shot": "镜头三", "visual": "画面切黑，只留下下一集标题式文案。", "subtitle": "下一集：答案马上揭晓。", "sound": "悬念鼓点"},
        ]
    variant_payload = public_remix_variant(variant)
    image_plan = remix_image_plan(choice, variant)
    video_plan = None
    return {
        "source": "local_fallback",
        "model_version": "remix-text-v1",
        "disclaimer": "AI 猜测剧情，非正片内容",
        "title": title,
        "logline": logline,
        "emotion": choice["tone"],
        "story_text": story_text,
        "storyboard": storyboard,
        "variant": variant_payload,
        "image_plan": image_plan,
        "video_plan": video_plan,
        "share_copy": f"我选择了「{choice['label']}」，AI 生成了一个非正片番外走向。",
        "prompt_trace": {
            "episode_id": episode.id,
            "drama_title": drama_title,
            "choice_key": choice["key"],
            "variant": variant_payload,
            "image_plan": image_plan,
            "video_plan": video_plan,
            "context_highlight_count": len(highlights),
        },
    }


def extract_llm_json(text: str) -> dict:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?", "", text).strip()
        text = re.sub(r"```$", "", text).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, re.S)
        if not match:
            raise
        return json.loads(match.group(0))


def normalize_remix_payload(raw: dict, fallback: dict) -> dict:
    storyboard = raw.get("storyboard") if isinstance(raw.get("storyboard"), list) else []
    clean_storyboard = []
    for index, item in enumerate(storyboard[:3], start=1):
        if not isinstance(item, dict):
            continue
        clean_storyboard.append(
            {
                "shot": str(item.get("shot") or f"镜头{index}"),
                "visual": str(item.get("visual") or ""),
                "subtitle": str(item.get("subtitle") or ""),
                "sound": str(item.get("sound") or ""),
            }
        )
    if len(clean_storyboard) != 3:
        clean_storyboard = fallback["storyboard"]
    return {
        **fallback,
        "source": "llm",
        "model_version": str(raw.get("model_version") or "doubao-remix-text-v1"),
        "title": str(raw.get("title") or fallback["title"]),
        "logline": str(raw.get("logline") or fallback["logline"]),
        "emotion": str(raw.get("emotion") or fallback["emotion"]),
        "story_text": str(raw.get("story_text") or fallback["story_text"]),
        "storyboard": clean_storyboard,
        "share_copy": str(raw.get("share_copy") or fallback["share_copy"]),
        "variant": fallback.get("variant"),
        "image_plan": fallback.get("image_plan"),
        "video_plan": fallback.get("video_plan"),
        "prompt_trace": fallback.get("prompt_trace", {}),
    }


def call_remix_llm(context: dict, choice: dict, variant: dict | None = None) -> dict:
    api_key = os.getenv("ARK_API_KEY")
    model = os.getenv("ARK_ENDPOINT_ID") or os.getenv("ARK_MODEL")
    base_url = os.getenv("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/")
    if not api_key or not model:
        raise RuntimeError("missing_llm_env")
    compact_context = {
        "drama_title": context.get("drama_title"),
        "episode_title": context.get("episode_title"),
        "genre": context.get("genre"),
        "duration_sec": context.get("duration_sec"),
        "recent_highlights": [
            {
                "time": item.get("time"),
                "title": item.get("title"),
                "description": str(item.get("description") or "")[:120],
                "evidence_text": str(item.get("evidence_text") or "")[:120],
            }
            for item in (context.get("highlights") or [])[-3:]
        ],
    }
    prompt = json.dumps(
        {
            "task": "生成片尾 AI 二创文字卡和三格分镜",
            "constraints": [
                "必须贴合 selected_variant，不要改掉这个版本的核心变量。",
                "按 30 秒以内预生成短片设计，节奏是 3 个镜头。",
                "如果是北往，人物保持打工返乡青年、旧摩托、年三十回家的现实气质。",
            ],
            "output_schema": {
                "title": "短标题",
                "logline": "一句话剧情钩子",
                "emotion": "情绪标签",
                "story_text": "120-180字剧情预测正文",
                "storyboard": [
                    {"shot": "镜头一", "visual": "画面", "subtitle": "字幕", "sound": "声音"},
                    {"shot": "镜头二", "visual": "画面", "subtitle": "字幕", "sound": "声音"},
                    {"shot": "镜头三", "visual": "画面", "subtitle": "字幕", "sound": "声音"},
                ],
                "share_copy": "可分享短句",
                "model_version": "模型版本",
            },
            "choice": choice,
            "selected_variant": {
                "public": public_remix_variant(variant),
                "story_seed": str(variant.get("story_text") or "")[:260] if variant else None,
            },
            "episode": compact_context,
        },
        ensure_ascii=False,
    )
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": REMIX_SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ],
        "temperature": 0.55,
        "max_tokens": 900,
    }
    request = urllib.request.Request(
        f"{base_url}/chat/completions",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=36) as response:
        data = json.loads(response.read().decode("utf-8"))
    return extract_llm_json(data["choices"][0]["message"]["content"])

def remix_record_payload(record: EpisodeAIRemix) -> dict:
    prompt_trace = load_json_field(record.prompt_trace_json, {})
    return {
        "id": record.id,
        "episode_id": record.episode_id,
        "choice_key": record.choice_key,
        "choice_label": record.choice_label,
        "choice": load_json_field(record.choice_json, {}),
        "source": record.source,
        "model_version": record.model_version,
        "disclaimer": record.disclaimer,
        "title": record.title,
        "logline": record.logline,
        "emotion": record.emotion,
        "story_text": record.story_text,
        "storyboard": load_json_field(record.storyboard_json, []),
        "share_copy": record.share_copy,
        "variant": prompt_trace.get("variant"),
        "image_plan": prompt_trace.get("image_plan"),
        "video_plan": prompt_trace.get("video_plan"),
        "prompt_trace": prompt_trace,
        "review_status": record.review_status,
        "review_note": record.review_note,
        "is_featured": record.is_featured,
        "featured_order": record.featured_order,
        "user": public_user(record.user) if record.user else None,
        "created_at": record.created_at.isoformat() if record.created_at else None,
        "updated_at": record.updated_at.isoformat() if record.updated_at else None,
    }


def create_remix_record(
    db: Session,
    episode: Episode,
    payload: EpisodeRemixCreate,
    choice: dict,
    result: dict,
    user: User | None,
) -> EpisodeAIRemix:
    prompt_trace = dict(result.get("prompt_trace") or {})
    if result.get("variant"):
        prompt_trace["variant"] = result["variant"]
    if result.get("image_plan"):
        prompt_trace["image_plan"] = result["image_plan"]
    if result.get("video_plan"):
        prompt_trace["video_plan"] = result["video_plan"]
    record = EpisodeAIRemix(
        episode_id=episode.id,
        user_id=user.id if user else None,
        session_id=payload.session_id,
        choice_key=choice["key"],
        choice_label=choice["label"],
        choice_json=json.dumps(choice, ensure_ascii=False),
        source=result.get("source", "local_fallback"),
        model_version=result.get("model_version", "remix-text-v1"),
        disclaimer=result.get("disclaimer", "AI 猜测剧情，非正片内容"),
        title=result.get("title", "AI 猜测卡"),
        logline=result.get("logline", ""),
        emotion=result.get("emotion", ""),
        story_text=result.get("story_text", ""),
        storyboard_json=json.dumps(result.get("storyboard", []), ensure_ascii=False),
        share_copy=result.get("share_copy", ""),
        prompt_trace_json=json.dumps(prompt_trace, ensure_ascii=False),
        review_status="draft",
        is_featured=False,
        featured_order=0,
    )
    db.add(record)
    db.commit()
    db.refresh(record)
    return record


def featured_remix_payloads(db: Session, episode_id: int, limit: int = 3) -> list[dict]:
    rows = (
        db.query(EpisodeAIRemix)
        .filter(EpisodeAIRemix.episode_id == episode_id, EpisodeAIRemix.is_featured.is_(True))
        .order_by(EpisodeAIRemix.featured_order.asc(), EpisodeAIRemix.updated_at.desc(), EpisodeAIRemix.id.desc())
        .limit(limit)
        .all()
    )
    return [remix_record_payload(row) for row in rows]


def normalize_editable_storyboard(storyboard: list[dict]) -> list[dict]:
    if len(storyboard) != 3:
        raise HTTPException(status_code=400, detail="分镜必须正好 3 个")
    normalized = []
    for index, item in enumerate(storyboard, start=1):
        if not isinstance(item, dict):
            raise HTTPException(status_code=400, detail="分镜必须是 JSON 对象")
        normalized.append(
            {
                "shot": str(item.get("shot") or f"镜头{index}").strip()[:32],
                "visual": str(item.get("visual") or "").strip()[:300],
                "subtitle": str(item.get("subtitle") or "").strip()[:160],
                "sound": str(item.get("sound") or "").strip()[:120],
            }
        )
    return normalized


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
        avatar_url=avatar_pool_url_for_seed(username),
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


@app.patch("/api/users/me/profile")
def update_my_profile(
    payload: UserProfileUpdate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    if payload.display_name is not None:
        user.display_name = payload.display_name.strip()
    if payload.avatar_url is not None:
        user.avatar_url = normalize_avatar_url(payload.avatar_url)
    db.commit()
    db.refresh(user)
    return {"user": public_user(user)}


@app.post("/api/users/me/avatar")
async def upload_my_avatar(
    avatar: UploadFile = File(...),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    original_name = avatar.filename or "avatar.png"
    suffix = Path(original_name).suffix.lower() or ".png"
    if suffix not in AVATAR_ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="头像仅支持 jpg、png、webp 或 gif")
    data = await avatar.read()
    if not data:
        raise HTTPException(status_code=400, detail="头像文件不能为空")
    if len(data) > AVATAR_MAX_BYTES:
        raise HTTPException(status_code=400, detail="头像文件不能超过 4MB")

    ensure_avatar_dirs()
    user_dir = AVATAR_ASSET_DIR / f"user_{user.id}"
    user_dir.mkdir(parents=True, exist_ok=True)
    safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", original_name)[-120:]
    stored_name = f"{uuid4().hex}_{safe_name}"
    stored_path = user_dir / stored_name
    stored_path.write_bytes(data)

    user.avatar_url = f"/media/avatars/user_{user.id}/{stored_name}"
    db.commit()
    db.refresh(user)
    return {"user": public_user(user)}


@app.get("/api/avatar-pool")
def list_avatar_pool() -> dict:
    return {
        "avatars": [
            {"url": f"/media/avatar-pool/{path.name}", "name": path.stem}
            for path in avatar_pool_files()
        ]
    }


@app.get("/api/users/me/friends")
def list_my_friends(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> dict:
    friend_rows = (
        db.query(UserFriend)
        .filter(UserFriend.user_id == user.id, UserFriend.status == "accepted")
        .order_by(UserFriend.created_at.desc(), UserFriend.id.desc())
        .all()
    )
    friend_ids = {row.friend_user_id for row in friend_rows}
    incoming_requests = (
        db.query(UserFriendRequest)
        .filter(UserFriendRequest.to_user_id == user.id, UserFriendRequest.status == "pending")
        .order_by(UserFriendRequest.created_at.desc(), UserFriendRequest.id.desc())
        .limit(20)
        .all()
    )
    outgoing_requests = (
        db.query(UserFriendRequest)
        .filter(UserFriendRequest.from_user_id == user.id, UserFriendRequest.status == "pending")
        .order_by(UserFriendRequest.created_at.desc(), UserFriendRequest.id.desc())
        .limit(20)
        .all()
    )
    request_history = (
        db.query(UserFriendRequest)
        .filter(or_(UserFriendRequest.from_user_id == user.id, UserFriendRequest.to_user_id == user.id))
        .order_by(UserFriendRequest.created_at.desc(), UserFriendRequest.id.desc())
        .limit(30)
        .all()
    )
    pending_user_ids = {row.from_user_id for row in incoming_requests} | {row.to_user_id for row in outgoing_requests}
    candidates = (
        db.query(User)
        .filter(User.id != user.id, User.is_active.is_(True))
        .order_by(User.id.asc())
        .all()
    )
    return {
        "friends": [friend_payload(row) for row in friend_rows],
        "incoming_requests": [friend_request_payload(row, user) for row in incoming_requests],
        "outgoing_requests": [friend_request_payload(row, user) for row in outgoing_requests],
        "request_history": [friend_request_payload(row, user) for row in request_history],
        "candidates": [
            user_brief(candidate)
            for candidate in candidates
            if candidate.id not in friend_ids and candidate.id not in pending_user_ids
        ],
    }


@app.post("/api/users/me/friends")
def request_my_friend(
    payload: FriendCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    friend = db.get(User, payload.user_id)
    if not friend or not friend.is_active:
        raise HTTPException(status_code=404, detail="好友用户不存在")
    if friend.id == user.id:
        raise HTTPException(status_code=400, detail="不能添加自己为好友")
    if are_friends(db, user.id, friend.id):
        response = list_my_friends(user, db)
        response["request_result"] = "already_friends"
        return response

    existing = latest_friend_request(db, user.id, friend.id)
    if existing and existing.status == "pending":
        if existing.to_user_id == user.id:
            existing.status = "accepted"
            existing.responded_at = datetime.utcnow()
            ensure_friend_edge(db, user.id, friend.id)
            ensure_friend_edge(db, friend.id, user.id)
            db.add(SocialNotification(user_id=friend.id, actor_user_id=user.id, event_type="friend_accept"))
            db.commit()
            response = list_my_friends(user, db)
            response["request_result"] = "accepted_incoming"
            return response
        response = list_my_friends(user, db)
        response["request_result"] = "already_pending"
        return response

    if existing:
        existing.from_user_id = user.id
        existing.to_user_id = friend.id
        existing.status = "pending"
        existing.created_at = datetime.utcnow()
        existing.responded_at = None
    else:
        db.add(UserFriendRequest(from_user_id=user.id, to_user_id=friend.id))
    db.add(SocialNotification(user_id=friend.id, actor_user_id=user.id, event_type="friend_request"))
    db.commit()
    response = list_my_friends(user, db)
    response["request_result"] = "sent"
    return response


@app.post("/api/users/me/friend-requests/{request_id}/accept")
def accept_friend_request(
    request_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    request = db.get(UserFriendRequest, request_id)
    if not request or request.to_user_id != user.id:
        raise HTTPException(status_code=404, detail="好友申请不存在")
    if request.status != "pending":
        raise HTTPException(status_code=400, detail="好友申请已处理")
    request.status = "accepted"
    request.responded_at = datetime.utcnow()
    ensure_friend_edge(db, user.id, request.from_user_id)
    ensure_friend_edge(db, request.from_user_id, user.id)
    db.add(SocialNotification(user_id=request.from_user_id, actor_user_id=user.id, event_type="friend_accept"))
    db.commit()
    response = list_my_friends(user, db)
    response["request_result"] = "accepted"
    return response


@app.post("/api/users/me/friend-requests/{request_id}/decline")
def decline_friend_request(
    request_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    request = db.get(UserFriendRequest, request_id)
    if not request or request.to_user_id != user.id:
        raise HTTPException(status_code=404, detail="好友申请不存在")
    if request.status == "pending":
        request.status = "declined"
        request.responded_at = datetime.utcnow()
        db.commit()
    response = list_my_friends(user, db)
    response["request_result"] = "declined"
    return response


@app.post("/api/users/me/friend-requests/{request_id}/withdraw")
def withdraw_friend_request(
    request_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    request = db.get(UserFriendRequest, request_id)
    if not request or request.from_user_id != user.id:
        raise HTTPException(status_code=404, detail="好友申请不存在")
    if request.status != "pending":
        raise HTTPException(status_code=400, detail="好友申请已处理，不能撤回")
    request.status = "withdrawn"
    request.responded_at = datetime.utcnow()
    db.commit()
    response = list_my_friends(user, db)
    response["request_result"] = "withdrawn"
    return response


@app.get("/api/chat/conversations")
def list_chat_conversations(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> dict:
    friend_rows = (
        db.query(UserFriend)
        .filter(UserFriend.user_id == user.id, UserFriend.status == "accepted")
        .order_by(UserFriend.created_at.desc(), UserFriend.id.desc())
        .all()
    )
    conversations = [chat_conversation_payload(row, user, db) for row in friend_rows]
    conversations.sort(
        key=lambda item: (
            item["last_message"]["created_at"] if item.get("last_message") else "",
            item["unread_count"],
        ),
        reverse=True,
    )
    return {"conversations": conversations, "unread_count": unread_chat_count(db, user.id)}


@app.get("/api/chat/messages/{friend_user_id}")
def list_chat_messages(
    friend_user_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    friend = db.get(User, friend_user_id)
    if not friend or not friend.is_active or not are_friends(db, user.id, friend.id):
        raise HTTPException(status_code=404, detail="好友会话不存在")
    unread_rows = (
        db.query(ChatMessage)
        .filter(ChatMessage.from_user_id == friend.id, ChatMessage.to_user_id == user.id, ChatMessage.read_at.is_(None))
        .all()
    )
    now = datetime.utcnow()
    for row in unread_rows:
        row.read_at = now
    if unread_rows:
        db.commit()
    rows = (
        db.query(ChatMessage)
        .filter(
            or_(
                and_(ChatMessage.from_user_id == user.id, ChatMessage.to_user_id == friend.id),
                and_(ChatMessage.from_user_id == friend.id, ChatMessage.to_user_id == user.id),
            )
        )
        .order_by(ChatMessage.created_at.desc(), ChatMessage.id.desc())
        .limit(80)
        .all()
    )
    messages = [chat_message_payload(row, user) for row in reversed(rows)]
    return {"friend": user_brief(friend), "messages": messages}


@app.post("/api/chat/messages")
def create_chat_message(
    payload: ChatMessageCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    friend = db.get(User, payload.to_user_id)
    if not friend or not friend.is_active or not are_friends(db, user.id, friend.id):
        raise HTTPException(status_code=404, detail="好友会话不存在")
    message_type = payload.message_type if payload.message_type in CHAT_MESSAGE_TYPES else "text"
    text = (payload.text or "").strip()
    if message_type == "text" and not text:
        raise HTTPException(status_code=400, detail="消息内容不能为空")
    if message_type == "emoji" and not text:
        text = "👍"
    if message_type == "watch_link" and not text:
        room_code = str((payload.payload or {}).get("room_code") or "").strip().upper()
        text = f"同看房间 {room_code}" if room_code else "发起了同看邀请"
    if message_type == "clip_card" and not text:
        text = str((payload.payload or {}).get("label") or "分享了一个热门片段").strip()
    message = ChatMessage(
        from_user_id=user.id,
        to_user_id=friend.id,
        message_type=message_type,
        text=text[:500],
        payload_json=json.dumps(payload.payload or {}, ensure_ascii=False),
    )
    db.add(message)
    db.commit()
    db.refresh(message)
    return {"message": chat_message_payload(message, user), "unread_count": unread_chat_count(db, friend.id)}


@app.get("/api/social/inbox")
def social_inbox(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> dict:
    friend_requests = (
        db.query(UserFriendRequest)
        .filter(UserFriendRequest.to_user_id == user.id, UserFriendRequest.status == "pending")
        .order_by(UserFriendRequest.created_at.desc(), UserFriendRequest.id.desc())
        .limit(6)
        .all()
    )
    invitations = (
        db.query(WatchRoomInvitation)
        .filter(WatchRoomInvitation.to_user_id == user.id, WatchRoomInvitation.status == "pending")
        .order_by(WatchRoomInvitation.created_at.desc(), WatchRoomInvitation.id.desc())
        .limit(6)
        .all()
    )
    notifications = (
        db.query(SocialNotification)
        .filter(SocialNotification.user_id == user.id)
        .order_by(SocialNotification.created_at.desc(), SocialNotification.id.desc())
        .limit(12)
        .all()
    )
    unread_social = (
        db.query(SocialNotification)
        .filter(SocialNotification.user_id == user.id, SocialNotification.is_read.is_(False))
        .count()
    )
    chat_unread = unread_chat_count(db, user.id)
    return {
        "unread_count": unread_social + len(invitations) + len(friend_requests) + chat_unread,
        "chat_unread_count": chat_unread,
        "friend_request_count": len(friend_requests),
        "room_invitation_count": len(invitations),
        "social_unread_count": unread_social,
        "friend_requests": [friend_request_payload(item, user) for item in friend_requests],
        "room_invitations": [invitation_payload(item, user) for item in invitations],
        "notifications": [social_notification_payload(item) for item in notifications],
    }


@app.post("/api/social/inbox/read")
def mark_social_inbox_read(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> dict:
    db.query(SocialNotification).filter(SocialNotification.user_id == user.id).update({"is_read": True})
    db.commit()
    return social_inbox(user, db)


@app.get("/api/social/feed")
def social_feed(
    scope: str = "all",
    topic: str = "",
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    rows = db.query(SocialPost).order_by(SocialPost.created_at.desc(), SocialPost.id.desc()).limit(80).all()
    visible_rows = []
    for post in rows:
        if topic and post.topic != topic:
            continue
        if scope == "mine" and post.user_id != user.id:
            continue
        if scope == "friends" and post.user_id != user.id and not are_friends(db, user.id, post.user_id):
            continue
        if can_view_social_post(post, user, db):
            visible_rows.append(post)
        if len(visible_rows) >= 30:
            break
    return {
        "scope": scope,
        "topic": topic,
        "topics": social_topic_cards(),
        "ranking": social_topic_ranking(topic, user, db) if topic else [],
        "posts": [social_post_payload(post, user, db) for post in visible_rows],
    }


@app.get("/api/social/topics/{topic}/ranking")
def get_social_topic_ranking(
    topic: str,
    limit: int = 10,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    return {
        "topic": topic,
        "topic_label": next((item["title"] for item in social_topic_cards() if item["key"] == topic), topic),
        "items": social_topic_ranking(topic, user, db, limit),
    }


@app.post("/api/social/voice-assets")
async def upload_social_voice_asset(
    voice_file: UploadFile = File(...),
    user: User = Depends(get_current_user),
) -> dict:
    original_name = voice_file.filename or "voice_imitation.webm"
    suffix = Path(original_name).suffix.lower() or ".webm"
    if suffix not in VOICE_ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="声音素材仅支持 wav、mp3、m4a、aac、ogg 或 webm")
    data = await voice_file.read()
    if not data:
        raise HTTPException(status_code=400, detail="声音素材不能为空")
    if len(data) > VOICE_MAX_BYTES:
        raise HTTPException(status_code=400, detail="声音素材不能超过 12MB")
    ensure_voice_dirs()
    filename = f"user_{user.id}_{uuid4().hex}{suffix}"
    path = SOCIAL_VOICE_ASSET_DIR / filename
    path.write_bytes(data)
    return {
        "asset_url": f"/media/social-voice-assets/{filename}",
        "asset_kind": "voice",
        "filename": filename,
        "size": len(data),
    }


@app.post("/api/social/posts")
def create_social_post(
    payload: SocialPostCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    visibility = payload.visibility if payload.visibility in SOCIAL_VISIBILITIES else "public"
    source_type = payload.source_type if payload.source_type in SOCIAL_SOURCE_TYPES else "thought"
    asset_kind = payload.asset_kind if payload.asset_kind in SOCIAL_ASSET_KINDS else "text"
    assert_social_text_safe(payload.title)
    assert_social_text_safe(payload.text)
    post = SocialPost(
        user_id=user.id,
        visibility=visibility,
        source_type=source_type,
        title=payload.title.strip(),
        text=payload.text.strip(),
        asset_kind=asset_kind,
        asset_url=payload.asset_url.strip(),
        asset_payload_json=json.dumps(payload.asset_payload, ensure_ascii=False),
        topic=payload.topic.strip(),
    )
    db.add(post)
    db.commit()
    db.refresh(post)
    return {"post": social_post_payload(post, user, db)}


@app.post("/api/social/posts/{post_id}/like")
def toggle_social_like(
    post_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    post = db.get(SocialPost, post_id)
    if not post:
        raise HTTPException(status_code=404, detail="动态不存在")
    ensure_social_post_visible(post, user, db)
    existing = (
        db.query(SocialReaction)
        .filter(SocialReaction.post_id == post.id, SocialReaction.user_id == user.id, SocialReaction.reaction_type == "like")
        .first()
    )
    if existing:
        db.delete(existing)
        liked = False
    else:
        db.add(SocialReaction(post_id=post.id, user_id=user.id, reaction_type="like"))
        notify_social_owner(db, post, user, "like")
        liked = True
    db.commit()
    db.refresh(post)
    return {"liked": liked, "post": social_post_payload(post, user, db)}


@app.post("/api/social/posts/{post_id}/comments")
def create_social_comment(
    post_id: int,
    payload: SocialCommentCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    post = db.get(SocialPost, post_id)
    if not post:
        raise HTTPException(status_code=404, detail="动态不存在")
    ensure_social_post_visible(post, user, db)
    assert_social_text_safe(payload.text)
    comment = SocialComment(post_id=post.id, user_id=user.id, text=payload.text.strip())
    db.add(comment)
    db.flush()
    notify_social_owner(db, post, user, "comment", comment.id)
    db.commit()
    db.refresh(post)
    return {"comment": social_comment_payload(comment), "post": social_post_payload(post, user, db)}


@app.delete("/api/social/comments/{comment_id}")
def delete_social_comment(
    comment_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    comment = db.get(SocialComment, comment_id)
    if not comment or comment.is_deleted:
        raise HTTPException(status_code=404, detail="评论不存在")
    post = comment.post
    if comment.user_id != user.id and post.user_id != user.id:
        raise HTTPException(status_code=403, detail="只有评论作者或动态发布者可以删除评论")
    comment.is_deleted = True
    db.commit()
    db.refresh(post)
    return {"ok": True, "post": social_post_payload(post, user, db)}


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


@app.get("/media/voice-clips/{filename}")
def voice_clip_media(filename: str) -> FileResponse:
    if not re.fullmatch(r"[a-f0-9]{32}\.mp3", filename):
        raise HTTPException(status_code=404, detail="语音文件不存在")
    path = VOICE_CLIP_DIR / filename
    if not path.exists():
        raise HTTPException(status_code=404, detail="语音文件不存在")
    return FileResponse(path, media_type="audio/mpeg", filename=filename)


@app.get("/media/social-voice-assets/{filename}")
def social_voice_asset_media(filename: str) -> FileResponse:
    if not re.fullmatch(r"user_\d+_[a-f0-9]{32}\.(wav|mp3|m4a|aac|ogg|webm)", filename):
        raise HTTPException(status_code=404, detail="声音素材不存在")
    path = SOCIAL_VOICE_ASSET_DIR / filename
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="声音素材不存在")
    media_type = {
        ".mp3": "audio/mpeg",
        ".m4a": "audio/mp4",
        ".aac": "audio/aac",
        ".ogg": "audio/ogg",
        ".webm": "audio/webm",
        ".wav": "audio/wav",
    }.get(path.suffix.lower(), "application/octet-stream")
    return FileResponse(path, media_type=media_type, filename=filename)


@app.get("/media/avatars/{user_folder}/{filename}")
def avatar_media(user_folder: str, filename: str) -> FileResponse:
    if not re.fullmatch(r"user_\d+", user_folder):
        raise HTTPException(status_code=404, detail="头像不存在")
    safe_name = Path(filename).name
    path = AVATAR_ASSET_DIR / user_folder / safe_name
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="头像不存在")
    media_type = {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".gif": "image/gif",
    }.get(path.suffix.lower(), "application/octet-stream")
    return FileResponse(path, media_type=media_type, filename=safe_name)


@app.get("/media/avatar-pool/{filename}")
def avatar_pool_media(filename: str) -> FileResponse:
    safe_name = Path(filename).name
    if safe_name != filename:
        raise HTTPException(status_code=404, detail="Avatar not found")
    path = AVATAR_POOL_DIR / safe_name
    if not path.exists() or not path.is_file():
        raise HTTPException(status_code=404, detail="Avatar not found")
    media_type = {
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp",
        ".gif": "image/gif",
    }.get(path.suffix.lower(), "application/octet-stream")
    return FileResponse(path, media_type=media_type, filename=safe_name)


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
        .filter(
            DanmakuComment.episode_id == episode_id,
            or_(DanmakuComment.review_status.is_(None), DanmakuComment.review_status == "approved"),
        )
        .order_by(DanmakuComment.time_sec.asc(), DanmakuComment.id.asc())
        .all()
    )
    return [danmaku_payload(row) for row in rows]


@app.get("/api/episodes/{episode_id}/experience")
def get_episode_experience(episode_id: int, db: Session = Depends(get_db)) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    row = db.query(EpisodeExperienceConfig).filter(EpisodeExperienceConfig.episode_id == episode_id).first()
    return parse_experience_config(row, episode)


@app.get("/api/episodes/{episode_id}/remix-options")
def get_episode_remix_options(episode_id: int, db: Session = Depends(get_db)) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    duration = float(episode.duration_sec or 0)
    return {
        "episode_id": episode.id,
        "drama_title": episode.drama.title,
        "episode_title": episode.title,
        "trigger_time_sec": round(max(0, duration - 8), 2),
        "disclaimer": "AI 猜测剧情，非正片内容",
        "options": remix_options_for_episode(episode),
        "featured_remixes": featured_remix_payloads(db, episode.id),
    }


@app.get("/api/episodes/{episode_id}/voice-imitation-activity")
def get_episode_voice_imitation_activity(
    episode_id: int,
    user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    payload = winter_voice_activity_payload(episode, db, user)
    if not payload["enabled"]:
        return {"enabled": False, "episode_id": episode.id, "drama_title": episode.drama.title, "lines": []}
    return payload


@app.post("/api/episodes/{episode_id}/ai-remix")
def create_episode_ai_remix(
    episode_id: int,
    payload: EpisodeRemixCreate,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    options = remix_options_for_episode(episode)
    choice = next((item for item in options if item["key"] == payload.choice_key), None)
    if not choice:
        raise HTTPException(status_code=400, detail="二创选项不存在")
    context = remix_context_payload(episode, db)
    if payload.variant_key and not is_beiwang_first_episode(episode):
        raise HTTPException(status_code=400, detail="该剧集暂不支持指定二创版本")
    variant = (
        remix_variant_for_choice(choice, payload.session_id, payload.variant_key)
        if is_beiwang_first_episode(episode)
        else None
    )
    if payload.variant_key and not variant:
        raise HTTPException(status_code=400, detail="二创版本不存在")
    fallback = fallback_remix_payload(episode, choice, context, variant)
    if fallback.get("image_plan", {}).get("asset_status") == "cached_images":
        result = {
            **fallback,
            "source": "cached_images",
            "model_version": "image-storyboard-cache-v1",
        }
    else:
        try:
            raw = call_remix_llm(context, choice, variant)
            result = normalize_remix_payload(raw, fallback)
        except Exception:
            result = fallback
    record = create_remix_record(db, episode, payload, choice, result, user)
    return {
        "ok": True,
        "episode_id": episode.id,
        "record_id": record.id,
        "review_status": record.review_status,
        "is_featured": record.is_featured,
        "choice": choice,
        **result,
    }


@app.post("/api/episodes/{episode_id}/remix-voice-clips")
def create_episode_remix_voice_clip(
    episode_id: int,
    payload: EpisodeRemixVoiceCreate,
    db: Session = Depends(get_db),
    user: User | None = Depends(get_optional_user),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    voice_mode = payload.voice_mode.strip().lower()
    if is_winter_solstice_first_episode(episode):
        line = next((item for item in WINTER_VOICE_LINES if item["key"] == payload.choice_key), None)
        if not line:
            raise HTTPException(status_code=400, detail="模仿台词不存在")
        if voice_mode != "user":
            raise HTTPException(status_code=400, detail="那年冬至模仿赛暂只支持用户声线生成")
        if not user:
            raise HTTPException(status_code=401, detail="请先登录后再生成我的声音版本")
        profile = active_voice_profile(db, user)
        if not profile:
            raise HTTPException(status_code=400, detail="请先在个人主页上传或录入声音样本")
        scene_key = f"winter_voice_match_{line['key']}"
        result = ensure_voice_clip(db, user, profile, VoiceClipCreate(text=line["text"], scene_key=scene_key))
        return {"voice_mode": "user", "line": line, **result}
    if not is_beiwang_first_episode(episode):
        raise HTTPException(status_code=400, detail="该剧集暂未配置片尾二创语音")
    options = remix_options_for_episode(episode)
    choice = next((item for item in options if item["key"] == payload.choice_key), None)
    if not choice:
        raise HTTPException(status_code=400, detail="二创选项不存在")
    variant = remix_variant_for_choice(choice, payload.session_id, payload.variant_key)
    if not variant:
        raise HTTPException(status_code=400, detail="二创版本不存在")
    image_plan = remix_image_plan(choice, variant)
    shot = next((item for item in image_plan["shots"] if int(item["index"]) == payload.shot_index), None)
    if not shot:
        raise HTTPException(status_code=400, detail="二创镜头不存在")
    text = str(shot.get("audio_text") or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="该镜头没有可生成语音的台词")

    scene_key = f"{image_plan['variant_slot']}_shot_{payload.shot_index}"
    if voice_mode == "user":
        if not user:
            raise HTTPException(status_code=401, detail="请先登录后再生成我的声音版本")
        profile = active_voice_profile(db, user)
        if not profile:
            raise HTTPException(status_code=400, detail="请先在个人主页上传声音样本")
        result = ensure_voice_clip(db, user, profile, VoiceClipCreate(text=text, scene_key=scene_key))
        return {"voice_mode": "user", **result}
    if voice_mode != "original":
        raise HTTPException(status_code=400, detail="声音版本仅支持 original 或 user")
    return ensure_original_remix_voice_clip(image_plan["variant_slot"], payload.shot_index, text)


@app.get("/api/episodes/{episode_id}/featured-remixes")
def get_featured_episode_remixes(episode_id: int, db: Session = Depends(get_db)) -> list[dict]:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    return featured_remix_payloads(db, episode.id)


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
        original_text=text,
        session_id=payload.session_id,
        mode=payload.mode,
    )
    comment.episode = episode
    governance = evaluate_comment(comment, 1)
    comment.review_status = governance.review_status
    comment.mode = governance.mode
    comment.risk_score = governance.risk_score
    comment.quality_score = governance.quality_score
    comment.spoiler_score = governance.spoiler_score
    comment.relevance_score = governance.relevance_score
    comment.cluster_key = governance.cluster_key
    comment.cluster_size = governance.cluster_size
    comment.suggested_time_sec = governance.suggested_time_sec
    comment.moderation_model_version = governance.layers["small_model"]["model_version"]
    comment.moderation_layers_json = json.dumps(governance.layers, ensure_ascii=False)
    comment.moderation_reason = governance.reason
    db.add(comment)
    db.commit()
    db.refresh(comment)
    if comment.review_status != "approved":
        raise HTTPException(
            status_code=400,
            detail={"category": comment.review_status, "message": comment.moderation_reason or "弹幕需要复核后展示"},
        )
    return danmaku_payload(comment)


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


@app.get("/api/users/me/voice-profile")
def get_my_voice_profile(user: User = Depends(get_current_user), db: Session = Depends(get_db)) -> dict:
    return voice_profile_payload(active_voice_profile(db, user), db)


@app.post("/api/users/me/voice-profile")
async def upload_my_voice_profile(
    consent_text: str = Form(...),
    voice_sample: UploadFile = File(...),
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    if consent_text.strip() != VOICE_CONSENT_TEXT:
        raise HTTPException(status_code=400, detail=f"请先朗读并确认固定授权文本：{VOICE_CONSENT_TEXT}")
    original_name = voice_sample.filename or "voice_sample.wav"
    suffix = Path(original_name).suffix.lower() or ".wav"
    if suffix not in VOICE_ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail="声音样本仅支持 wav、mp3、m4a、aac、ogg 或 webm")
    data = await voice_sample.read()
    if not data:
        raise HTTPException(status_code=400, detail="声音样本不能为空")
    if len(data) > VOICE_MAX_BYTES:
        raise HTTPException(status_code=400, detail="声音样本不能超过 12MB")

    ensure_voice_dirs()
    user_dir = VOICE_PROMPT_DIR / f"user_{user.id}"
    user_dir.mkdir(parents=True, exist_ok=True)
    safe_name = re.sub(r"[^a-zA-Z0-9_.-]", "_", original_name)[-120:]
    stored_name = f"{uuid4().hex}_{safe_name}"
    stored_path = user_dir / stored_name
    stored_path.write_bytes(data)
    prompt_path = normalize_voice_prompt_audio(stored_path, suffix)

    db.query(VoiceProfile).filter(VoiceProfile.user_id == user.id, VoiceProfile.status == "active").update(
        {"status": "replaced", "updated_at": datetime.utcnow()}
    )
    profile = VoiceProfile(
        user_id=user.id,
        status="active",
        source="user_upload",
        consent_text=VOICE_CONSENT_TEXT,
        prompt_text=VOICE_CONSENT_TEXT,
        prompt_audio_path=str(prompt_path),
        prompt_audio_filename=original_name,
    )
    db.add(profile)
    db.commit()
    db.refresh(profile)
    return voice_profile_payload(profile, db)


@app.post("/api/users/me/voice-clips")
def create_my_voice_clip(
    payload: VoiceClipCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    profile = active_voice_profile(db, user)
    if not profile:
        raise HTTPException(status_code=400, detail="请先在个人主页上传声音样本")
    return ensure_voice_clip(db, user, profile, payload)


@app.get("/api/users/{user_id}/growth")
def get_user_growth(
    user_id: int,
    _viewer: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    user = db.get(User, user_id)
    if not user or not user.is_active:
        raise HTTPException(status_code=404, detail="用户不存在")
    return {"user": user_brief(user), **reward_profile(user, db)}


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


@app.get("/api/watch-rooms/invitations")
def list_watch_room_invitations(
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    received = (
        db.query(WatchRoomInvitation)
        .filter(WatchRoomInvitation.to_user_id == user.id, WatchRoomInvitation.status == "pending")
        .order_by(WatchRoomInvitation.created_at.desc(), WatchRoomInvitation.id.desc())
        .limit(20)
        .all()
    )
    sent = (
        db.query(WatchRoomInvitation)
        .filter(WatchRoomInvitation.from_user_id == user.id)
        .order_by(WatchRoomInvitation.created_at.desc(), WatchRoomInvitation.id.desc())
        .limit(20)
        .all()
    )
    return {
        "received": [invitation_payload(item, user) for item in received],
        "sent": [invitation_payload(item, user) for item in sent],
    }


@app.post("/api/watch-rooms/{code}/invite")
def invite_watch_room_friend(
    code: str,
    payload: WatchRoomInvitationCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    room = get_watch_room(code, db)
    ensure_room_member(room, user)
    friend = db.get(User, payload.user_id)
    if not friend or not friend.is_active:
        raise HTTPException(status_code=404, detail="邀请用户不存在")
    if friend.id == user.id:
        raise HTTPException(status_code=400, detail="不能邀请自己")
    if not are_friends(db, user.id, friend.id):
        raise HTTPException(status_code=400, detail="请先添加为好友，再发起同看邀请")
    if friend.id in {room.host_user_id, room.guest_user_id}:
        raise HTTPException(status_code=400, detail="好友已经在房间内")
    if room.guest_user_id is not None:
        raise HTTPException(status_code=409, detail="房间已满，暂时只支持双人同看")

    invitation = (
        db.query(WatchRoomInvitation)
        .filter(
            WatchRoomInvitation.room_id == room.id,
            WatchRoomInvitation.to_user_id == friend.id,
            WatchRoomInvitation.status == "pending",
        )
        .first()
    )
    if invitation:
        invitation.from_user_id = user.id
        invitation.created_at = datetime.utcnow()
    else:
        invitation = WatchRoomInvitation(room_id=room.id, from_user_id=user.id, to_user_id=friend.id)
        db.add(invitation)
    db.commit()
    db.refresh(invitation)
    return invitation_payload(invitation, user)


@app.post("/api/watch-rooms/invitations/{invitation_id}/accept")
def accept_watch_room_invitation(
    invitation_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    invitation = db.get(WatchRoomInvitation, invitation_id)
    if not invitation or invitation.to_user_id != user.id:
        raise HTTPException(status_code=404, detail="邀请不存在")
    room = invitation.room
    if invitation.status not in {"pending", "accepted"}:
        raise HTTPException(status_code=400, detail="邀请已处理")
    if user.id != room.host_user_id and room.guest_user_id not in {None, user.id}:
        invitation.status = "declined"
        invitation.responded_at = datetime.utcnow()
        db.commit()
        raise HTTPException(status_code=409, detail="房间已满")
    if user.id != room.host_user_id and room.guest_user_id is None:
        room.guest_user_id = user.id
    invitation.status = "accepted"
    invitation.responded_at = datetime.utcnow()
    room.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(invitation)
    db.refresh(room)
    return {"invitation": invitation_payload(invitation, user), "room": room_payload(room, user)}


@app.post("/api/watch-rooms/invitations/{invitation_id}/decline")
def decline_watch_room_invitation(
    invitation_id: int,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    invitation = db.get(WatchRoomInvitation, invitation_id)
    if not invitation or invitation.to_user_id != user.id:
        raise HTTPException(status_code=404, detail="邀请不存在")
    if invitation.status == "pending":
        invitation.status = "declined"
        invitation.responded_at = datetime.utcnow()
        db.commit()
        db.refresh(invitation)
    return {"invitation": invitation_payload(invitation, user)}


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


@app.get("/api/watch-rooms/{code}/events")
def list_watch_room_events(
    code: str,
    after_id: int = 0,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> list[dict]:
    room = get_watch_room(code, db)
    ensure_room_member(room, user)
    events = (
        db.query(WatchRoomEvent)
        .filter(WatchRoomEvent.room_id == room.id, WatchRoomEvent.id > max(0, after_id))
        .order_by(WatchRoomEvent.id.asc())
        .limit(40)
        .all()
    )
    return [public_room_event(event) for event in events]


@app.post("/api/watch-rooms/{code}/events")
def create_watch_room_event(
    code: str,
    payload: WatchRoomEventCreate,
    user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
) -> dict:
    room = get_watch_room(code, db)
    ensure_room_member(room, user)
    event_type = payload.event_type.strip()
    if event_type not in ROOM_EVENT_TYPES:
        raise HTTPException(status_code=400, detail="不支持的房间事件")
    event = WatchRoomEvent(
        room_id=room.id,
        user_id=user.id,
        event_type=event_type,
        payload_json=json.dumps(payload.payload, ensure_ascii=False),
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return public_room_event(event)


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
        "ai_remix_count": db.query(EpisodeAIRemix).count(),
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
        "avatar_url": user.avatar_url or "",
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


def danmaku_layer_payload(row: DanmakuComment) -> dict:
    return safe_json_loads(row.moderation_layers_json, {})


def danmaku_small_model_metadata() -> dict:
    if not SMALL_MODEL_PATH.exists():
        return {}
    try:
        return json.loads(SMALL_MODEL_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {"version": "small-model-broken", "trained_rows": 0}


def danmaku_governance_summary(rows: list[DanmakuComment]) -> dict:
    status_counter = Counter(row.review_status or "approved" for row in rows)
    mode_counter = Counter(row.mode or "light" for row in rows)
    layer_payloads = [danmaku_layer_payload(row) for row in rows]
    duplicate_groups = {
        row.cluster_key
        for row in rows
        if row.cluster_key and int(row.cluster_size or 1) > 1
    }
    duplicate_comments = sum(1 for row in rows if int(row.cluster_size or 1) > 1)
    llm_candidate_count = sum(1 for layers in layer_payloads if layers.get("llm_review", {}).get("candidate"))
    small_model_low_confidence = sum(
        1
        for layers in layer_payloads
        if float(layers.get("small_model", {}).get("confidence", 1) or 0) < 0.22
    )
    semantic_issue_count = sum(1 for layers in layer_payloads if layers.get("semantic", {}).get("issues"))
    rule_issue_count = sum(1 for layers in layer_payloads if layers.get("rule", {}).get("issues"))
    time_delay_count = sum(
        1
        for row, layers in zip(rows, layer_payloads)
        if row.suggested_time_sec is not None or layers.get("time_aware", {}).get("suggested_time_sec") is not None
    )
    small_model = danmaku_small_model_metadata()
    model_versions = sorted({row.moderation_model_version for row in rows if row.moderation_model_version})
    layer_counts = {
        "rule": rule_issue_count,
        "time_aware": time_delay_count,
        "semantic": semantic_issue_count,
        "cluster": duplicate_comments,
        "small_model": len(rows),
        "llm_review": llm_candidate_count,
        "human_review": status_counter.get("needs_review", 0),
    }
    pipeline = [
        {"key": "rule", "label": "规则层", "count": layer_counts["rule"], "description": "低俗、广告、联系方式、刷屏、过长先拦截。"},
        {"key": "time_aware", "label": "时间感知", "count": layer_counts["time_aware"], "description": "结合剧情揭晓点，剧透弹幕延后或复核。"},
        {"key": "semantic", "label": "语义分类", "count": layer_counts["semantic"], "description": "判断是否相关、出戏、低质量或情绪价值高。"},
        {"key": "cluster", "label": "聚类去重", "count": layer_counts["cluster"], "description": "相似弹幕合并，只优先看代表样本。"},
        {"key": "small_model", "label": "小模型", "count": layer_counts["small_model"], "description": "用人工复核结果训练轻量可解释词权重模型。"},
        {"key": "llm_review", "label": "大模型复审", "count": layer_counts["llm_review"], "description": "低置信度、高风险、高价值样本进入离线复审队列。"},
        {"key": "human_review", "label": "人工复核", "count": layer_counts["human_review"], "description": "只处理模型拿不准或将进入推荐池的弹幕。"},
    ]
    return {
        "total": len(rows),
        "status": dict(status_counter),
        "mode": dict(mode_counter),
        "needs_review": status_counter.get("needs_review", 0),
        "hidden": status_counter.get("hidden", 0),
        "approved": status_counter.get("approved", 0),
        "layer_counts": layer_counts,
        "pipeline": pipeline,
        "duplicate_group_count": len(duplicate_groups),
        "llm_candidate_count": llm_candidate_count,
        "model": {
            "governance_versions": model_versions,
            "small_model_version": small_model.get("version", "small-model-default"),
            "small_model_trained_rows": small_model.get("trained_rows", 0),
            "small_model_updated_at": small_model.get("updated_at", ""),
            "small_model_low_confidence": small_model_low_confidence,
            "llm_review_mode": "offline_batch_queue",
        },
    }


@app.get("/api/admin/episodes/{episode_id}/danmaku-governance")
def admin_list_episode_danmaku_governance(
    episode_id: int,
    status: str = "needs_review",
    mode: str = "all",
    q: str = "",
    limit: int = 240,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    rows = (
        db.query(DanmakuComment)
        .filter(DanmakuComment.episode_id == episode_id)
        .order_by(DanmakuComment.time_sec.asc(), DanmakuComment.id.asc())
        .all()
    )
    filtered = rows
    if status != "all":
        filtered = [row for row in filtered if (row.review_status or "approved") == status]
    if mode != "all":
        filtered = [row for row in filtered if (row.mode or "light") == mode]
    keyword = q.strip()
    if keyword:
        filtered = [row for row in filtered if keyword in row.text or keyword in (row.moderation_reason or "")]
    filtered.sort(
        key=lambda row: (
            0 if (row.review_status or "approved") == "needs_review" else 1,
            -(row.risk_score or 0),
            -(row.spoiler_score or 0),
            row.time_sec,
        )
    )
    safe_limit = max(20, min(500, int(limit or 240)))
    return {
        "episode": episode_review_meta(episode),
        "summary": danmaku_governance_summary(rows),
        "items": [danmaku_payload(row, include_governance=True) for row in filtered[:safe_limit]],
    }


@app.post("/api/admin/episodes/{episode_id}/danmaku-governance/run")
def admin_run_episode_danmaku_governance(
    episode_id: int,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    return apply_governance_to_episode(db, episode_id)


@app.post("/api/admin/episodes/{episode_id}/danmaku-governance/train-small-model")
def admin_train_episode_danmaku_small_model(
    episode_id: int,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    trained = train_small_model_from_db(db)
    rerun = apply_governance_to_episode(db, episode_id)
    return {
        "trained": trained,
        "rerun": rerun,
        "small_model_path": str(SMALL_MODEL_PATH.relative_to(ROOT_DIR)),
    }


@app.patch("/api/admin/danmaku/{comment_id}")
def admin_update_danmaku_review(
    comment_id: int,
    payload: DanmakuReviewUpdate,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    comment = db.get(DanmakuComment, comment_id)
    if not comment:
        raise HTTPException(status_code=404, detail="弹幕不存在")
    if payload.text is not None:
        comment.text = payload.text.strip()
    if payload.time_sec is not None:
        duration = comment.episode.duration_sec or payload.time_sec
        comment.time_sec = round(min(max(0, payload.time_sec), duration), 2)
    if payload.mode is not None:
        if payload.mode not in {"light", "carnival", "curated", "seed"}:
            raise HTTPException(status_code=400, detail="弹幕模式不合法")
        comment.mode = payload.mode
    if payload.review_status is not None:
        if payload.review_status not in {"approved", "needs_review", "hidden"}:
            raise HTTPException(status_code=400, detail="复核状态不合法")
        comment.review_status = payload.review_status
    if payload.moderation_reason is not None:
        comment.moderation_reason = payload.moderation_reason.strip()
    db.commit()
    db.refresh(comment)
    return danmaku_payload(comment, include_governance=True)


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


@app.get("/api/admin/episodes/{episode_id}/remixes")
def admin_list_episode_remixes(
    episode_id: int,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> list[dict]:
    episode = db.get(Episode, episode_id)
    if not episode:
        raise HTTPException(status_code=404, detail="剧集不存在")
    rows = (
        db.query(EpisodeAIRemix)
        .filter(EpisodeAIRemix.episode_id == episode.id)
        .order_by(EpisodeAIRemix.is_featured.desc(), EpisodeAIRemix.featured_order.asc(), EpisodeAIRemix.id.desc())
        .all()
    )
    return [remix_record_payload(row) for row in rows]


@app.patch("/api/admin/remixes/{remix_id}")
def admin_update_remix_review(
    remix_id: int,
    payload: EpisodeRemixReviewUpdate,
    db: Session = Depends(get_db),
    _user: User = Depends(require_roles("admin", "reviewer")),
) -> dict:
    record = db.get(EpisodeAIRemix, remix_id)
    if not record:
        raise HTTPException(status_code=404, detail="二创记录不存在")
    allowed_statuses = {"draft", "featured", "hidden"}
    if payload.review_status is not None:
        status = payload.review_status.strip()
        if status not in allowed_statuses:
            raise HTTPException(status_code=400, detail="状态只能是 draft、featured 或 hidden")
        record.review_status = status
        if status == "featured":
            record.is_featured = True
        if status == "hidden":
            record.is_featured = False
    if payload.is_featured is not None:
        record.is_featured = payload.is_featured
        record.review_status = "featured" if payload.is_featured else "draft"
    if payload.review_note is not None:
        record.review_note = payload.review_note.strip()
    if payload.featured_order is not None:
        record.featured_order = payload.featured_order
    if payload.title is not None:
        title = payload.title.strip()
        if not title:
            raise HTTPException(status_code=400, detail="标题不能为空")
        record.title = title
    if payload.logline is not None:
        record.logline = payload.logline.strip()
    if payload.emotion is not None:
        record.emotion = payload.emotion.strip()
    if payload.story_text is not None:
        record.story_text = payload.story_text.strip()
    if payload.share_copy is not None:
        record.share_copy = payload.share_copy.strip()
    if payload.storyboard is not None:
        record.storyboard_json = json.dumps(normalize_editable_storyboard(payload.storyboard), ensure_ascii=False)
    record.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(record)
    return remix_record_payload(record)


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


@app.api_route("/downloads/banju-debug.apk", methods=["GET", "HEAD"], include_in_schema=False)
def download_banju_android_apk() -> FileResponse:
    path = FRONTEND_DIR / "assets" / "downloads" / "banju-debug.apk"
    if not path.exists():
        raise HTTPException(status_code=404, detail="APK not found")
    return FileResponse(
        path,
        media_type="application/vnd.android.package-archive",
        filename="banju-debug.apk",
    )


if FRONTEND_DIR.exists():
    app.mount("/", StaticFiles(directory=FRONTEND_DIR, html=True), name="frontend")
