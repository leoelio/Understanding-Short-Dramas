import json
import hashlib
import os
import re
import urllib.request
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
    EpisodeAIRemix,
    Episode,
    EpisodeExperienceConfig,
    Highlight,
    Interaction,
    User,
    UserReward,
    WatchHistory,
    WatchRoom,
    WatchRoomEvent,
)
from .schemas import (
    DanmakuCreate,
    EpisodeRemixCreate,
    EpisodeRemixReviewUpdate,
    ExperienceConfigUpdate,
    InteractionCreate,
    LoginRequest,
    RegisterRequest,
    UserAdminUpdate,
    WatchHistoryUpdate,
    WatchRoomCreate,
    WatchRoomEventCreate,
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
        "role": user.role,
        "growth_title": title,
        "points": points,
        "badge_count": len(rewards),
        "latest_badges": [reward_payload(item) for item in rewards[:3]],
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
            "variant_key": "chain_bridge",
            "label": "链条断在小桥",
            "variable_label": "困难：镇外小桥断链",
            "summary": "摩托链条在镇外小桥断了，雪越下越大，他借手电和铁丝临时修好继续赶路。",
            "runtime_sec": 26,
            "story_text": "年三十的风把路吹得发硬，摩托刚上镇外小桥就猛地一顿，链条掉进雪泥里。手机只剩 8% 电，修车铺早已落锁。他蹲在桥边把手冻红，正想放弃时，一个路过的大爷递来手电和一截铁丝。他把链条临时接上，发动机重新响起，他冲着黑路低声说：妈，我往回赶呢。",
            "storyboard": [
                {"shot": "镜头一", "visual": "旧摩托冲上镇外小桥，车身猛地一歪，链条甩进雪泥。", "subtitle": "“不是吧，就差这段路了。”", "sound": "金属断响和风声"},
                {"shot": "镜头二", "visual": "主角蹲在桥边修车，手被冻红，老人用手电替他照着链条。", "subtitle": "“孩子，回家这事儿，别半道停。”", "sound": "手电开关声"},
                {"shot": "镜头三", "visual": "摩托重新点火，车灯划开雪夜，他把围巾往上拉继续上路。", "subtitle": "“妈，等我。”", "sound": "发动机重新轰鸣"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "小桥断链", "video_prompt": "现实主义短剧画面，东北年三十雪夜，打工返乡青年穿朴素冬装，骑旧摩托经过镇外小桥，链条断裂甩进雪泥，冷色电影感，移动端竖屏，克制但有冲击力", "sound": "链条断裂、寒风"},
                {"duration_sec": 10, "caption": "借光修车", "video_prompt": "桥边近景，青年蹲在雪地修摩托链条，手冻红，一位路过老人递手电照明，旧行李绑在车尾，人物表情坚韧，写实温暖光线", "sound": "手电咔哒、呼吸声"},
                {"duration_sec": 8, "caption": "继续上路", "video_prompt": "旧摩托重新发动，车灯照亮乡道积雪，青年拉紧围巾继续往家赶，远处村口有微弱灯火，返乡悬念和希望感", "sound": "发动机轰鸣"},
            ],
        },
        {
            "variant_key": "flat_tire",
            "label": "轮胎扎进铁钉",
            "variable_label": "困难：荒路爆胎",
            "summary": "半路轮胎扎钉，寒风里没人修车，他用工地胶布和借来的补胎工具撑到下一站。",
            "runtime_sec": 27,
            "story_text": "摩托刚过废弃收费站，后轮突然一软，铁钉扎进轮胎。导航显示离家还有几十公里，路边小卖部也准备关门。他摸出最后一点零钱买了热水，老板娘看他发抖，翻出一套旧补胎工具。胶条塞进去的那一刻，他听见母亲发来的语音：路上慢点。于是他把语音按了收藏，重新扶起摩托。",
            "storyboard": [
                {"shot": "镜头一", "visual": "荒路上旧摩托后轮泄气，铁钉在轮胎上反光。", "subtitle": "“还有几十公里，不能在这儿停。”", "sound": "轮胎漏气声"},
                {"shot": "镜头二", "visual": "小卖部半拉卷帘门，老板娘递出热水和旧补胎工具。", "subtitle": "“先暖暖手，再补。”", "sound": "卷帘门摩擦声"},
                {"shot": "镜头三", "visual": "主角听完母亲语音，把手机贴近胸口，推车重新上路。", "subtitle": "“嗯，马上回。”", "sound": "微信语音提示"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "荒路爆胎", "video_prompt": "东北返乡公路，废弃收费站旁，旧摩托后轮被铁钉扎破，青年下车查看，车尾绑着行李，冷风吹过空路，写实竖屏短剧", "sound": "漏气、风声"},
                {"duration_sec": 10, "caption": "小卖部补胎", "video_prompt": "乡镇小卖部半关门，暖黄色灯光，老板娘递给返乡青年热水和旧补胎工具，青年手冻红但眼神坚定，温暖现实主义", "sound": "卷帘门、热水杯"},
                {"duration_sec": 9, "caption": "听语音上路", "video_prompt": "青年在路灯下听母亲语音，把手机贴近胸口，然后扶起补好的摩托继续赶路，远处公路延伸进夜色", "sound": "语音提示、摩托点火"},
            ],
        },
        {
            "variant_key": "frozen_engine",
            "label": "发动机冻住",
            "variable_label": "困难：夜里打不着火",
            "summary": "夜里发动机被冻住，他手机低电、路边无车，只能用热水和耐心把摩托重新唤醒。",
            "runtime_sec": 25,
            "story_text": "夜越深，摩托越不争气。主角在路边连踩几脚，发动机只闷闷响了两声就彻底沉默。手机电量红了，地图也开始卡。他盯着后备箱里的年货，差点把头埋进围巾里。远处环卫站的灯还亮着，值班大叔给他倒了一壶热水。他一点点暖发动机，像哄一个也想回家的老伙计。",
            "storyboard": [
                {"shot": "镜头一", "visual": "路边空旷，主角反复踩启动杆，摩托只抖不响。", "subtitle": "“你也想歇了？”", "sound": "空踩声"},
                {"shot": "镜头二", "visual": "环卫站暖灯下，大叔把热水壶递给他。", "subtitle": "“车冷，人也冷，慢慢来。”", "sound": "热水倒入杯中"},
                {"shot": "镜头三", "visual": "白雾从发动机旁升起，摩托终于点火，主角笑了一下。", "subtitle": "“走，回家。”", "sound": "发动机低鸣"},
            ],
            "video_shots": [
                {"duration_sec": 7, "caption": "打不着火", "video_prompt": "寒冷夜路，青年反复踩旧摩托启动杆，发动机冻住只抖动不点火，手机红色低电，行李和年货绑在后座，现实主义", "sound": "启动失败声"},
                {"duration_sec": 9, "caption": "环卫站热水", "video_prompt": "路边环卫站暖灯，值班大叔递热水壶给返乡青年，雪夜白雾，人物质朴，有人情味，竖屏电影质感", "sound": "热水声"},
                {"duration_sec": 9, "caption": "唤醒摩托", "video_prompt": "青年用热水和布慢慢暖发动机，白雾升起，旧摩托终于启动，青年露出疲惫但开心的笑，继续返乡", "sound": "发动机启动"},
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
            "variant_key": "standing_ticket",
            "label": "借钱买站票",
            "variable_label": "票种：临时站票",
            "summary": "暴雪封路让骑车变危险，他借钱抢到临时站票，挤上车继续赶年三十。",
            "runtime_sec": 27,
            "story_text": "暴雪预警一响，路口的交警把摩托全拦了下来。主角看着手机上跳出的红色封路提示，嘴硬了半天，最后还是给工友发了一句：站票也行，帮我抢一张。车站里人挤人，他背着行李站在过道，鞋边还沾着雪泥。有人抱怨太挤，他却笑了一下：挤点好，挤说明大家都在往家赶。",
            "storyboard": [
                {"shot": "镜头一", "visual": "路口警示灯闪烁，交警拦下摩托，暴雪压低视线。", "subtitle": "“前面封了，不能骑。”", "sound": "警示喇叭"},
                {"shot": "镜头二", "visual": "主角盯着手机抢票页面，最后点下确认。", "subtitle": "“站票也行，能回去就行。”", "sound": "抢票提示音"},
                {"shot": "镜头三", "visual": "拥挤车厢过道，主角背着行李站稳，鞋边雪泥慢慢化开。", "subtitle": "“大家都在往家赶。”", "sound": "车厢人声"},
            ],
            "video_shots": [
                {"duration_sec": 8, "caption": "暴雪封路", "video_prompt": "东北公路暴雪，路口警示灯和交警拦下旧摩托，青年披着雪站在车旁，返乡路被迫中断，竖屏写实", "sound": "警示喇叭"},
                {"duration_sec": 8, "caption": "抢站票", "video_prompt": "青年在车站角落盯着手机抢票页面，屏幕显示临时站票，手指犹豫后点下确认，周围人群焦急", "sound": "手机提示"},
                {"duration_sec": 11, "caption": "挤上车", "video_prompt": "拥挤火车过道，青年背着行李站稳，鞋边雪泥融化，车窗外夜色飞过，他露出释然笑意", "sound": "车厢人声"},
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
            "variant_key": "convertible",
            "label": "敞篷跑车热血顺路",
            "variable_label": "车主：敞篷跑车",
            "summary": "他帮跑车车主盖住坏掉的车篷，摩托被偷后，对方用夸张但热血的方式载他回家。",
            "runtime_sec": 25,
            "story_text": "最荒唐的是，雪夜里竟然停着一辆敞篷跑车，车篷卡住，车主冻得直打哆嗦。主角把自己的防水布拆下来帮他盖车，嘴上还吐槽：你这也挺摇滚。结果一回头，摩托被偷了。车主愣了两秒，忽然把暖风开到最大：哥们，上车，我这车虽然离谱，但往北开没问题。于是返乡路突然从苦情变成了热血喜剧。",
            "storyboard": [
                {"shot": "镜头一", "visual": "雪夜敞篷跑车卡在路边，车主抱着头发抖。", "subtitle": "“你这也太摇滚了。”", "sound": "夸张冷风声"},
                {"shot": "镜头二", "visual": "主角用防水布帮盖车，再回头发现摩托没了。", "subtitle": "“不是，摇滚到我车没了？”", "sound": "喜剧停顿"},
                {"shot": "镜头三", "visual": "跑车暖风开满，主角抱着行李坐上副驾，车灯冲进雪夜。", "subtitle": "“走，东北方向！”", "sound": "发动机高转"},
            ],
            "video_shots": [
                {"duration_sec": 7, "caption": "离谱跑车", "video_prompt": "雪夜乡路，一辆敞篷跑车车篷卡住，车主冻得发抖，返乡青年骑旧摩托停下吐槽，轻喜剧但写实", "sound": "冷风呼啸"},
                {"duration_sec": 8, "caption": "帮盖车篷", "video_prompt": "青年拆下摩托防水布帮敞篷跑车盖车，动作利落，下一秒回头发现旧摩托被偷，表情夸张懵住", "sound": "喜剧停顿"},
                {"duration_sec": 10, "caption": "热血向北", "video_prompt": "跑车暖风开满，青年抱行李坐上副驾，车灯穿过雪夜，路线牌指向东北，现实喜剧和返乡热血结合", "sound": "跑车发动机"},
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


def remix_variant_for_choice(choice: dict, session_id: str) -> dict | None:
    variants = BEIWANG_EP1_REMIX_VARIANTS.get(choice["key"], [])
    if not variants:
        return None
    digest = hashlib.sha1(f"{session_id}:{choice['key']}".encode("utf-8")).hexdigest()
    return variants[int(digest[:8], 16) % len(variants)]


def remix_video_plan(choice: dict, variant: dict | None) -> dict | None:
    if not variant:
        return None
    slot = f"beiwang_ep1_{choice['key']}_{variant['variant_key']}"
    return {
        "asset_status": "script_ready",
        "render_mode": "pre_generated_video",
        "target_duration_sec": variant["runtime_sec"],
        "variant_slot": slot,
        "replacement_axis": variant["variable_label"],
        "storage_hint": f"/assets/remix_videos/beiwang_ep1/{slot}.mp4",
        "note": "赛前可按该视频提示词批量生成并缓存，客户端按用户 session 分配不同版本。",
        "shots": variant["video_shots"],
    }


def remix_options_for_episode(episode: Episode) -> list[dict]:
    title = episode.drama.title
    if is_beiwang_first_episode(episode):
        return [
            {
                "key": "road_breakdown",
                "label": "车坏在半路",
                "description": "摩托骑到半路出故障，他遇到风雪、断链或打不着火的困难，修好后继续赶回家。",
                "tone": "返乡闯关",
                "icon": "修",
                "variant_count": 3,
                "target_duration_sec": 28,
            },
            {
                "key": "ticket_home",
                "label": "借钱买票回家",
                "description": "骑车太苦让他动摇，路上遇到一个具体事件后，他放下逞强，借钱买票继续回家。",
                "tone": "现实共情",
                "icon": "票",
                "variant_count": 3,
                "target_duration_sec": 28,
            },
            {
                "key": "kindness_ride",
                "label": "帮人后一起回家",
                "description": "他路上帮了别人，摩托却被偷；被帮助的人正好也往东北走，最后载他一起回家。",
                "tone": "善意反转",
                "icon": "善",
                "variant_count": 3,
                "target_duration_sec": 28,
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
    video_plan = remix_video_plan(choice, variant)
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
        "video_plan": video_plan,
        "share_copy": f"我选择了「{choice['label']}」，AI 生成了一个非正片番外走向。",
        "prompt_trace": {
            "episode_id": episode.id,
            "drama_title": drama_title,
            "choice_key": choice["key"],
            "variant": variant_payload,
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


def load_json_field(value: str | None, fallback):
    if not value:
        return fallback
    try:
        return json.loads(value)
    except json.JSONDecodeError:
        return fallback


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
    variant = remix_variant_for_choice(choice, payload.session_id) if is_beiwang_first_episode(episode) else None
    fallback = fallback_remix_payload(episode, choice, context, variant)
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


if FRONTEND_DIR.exists():
    app.mount("/", StaticFiles(directory=FRONTEND_DIR, html=True), name="frontend")
