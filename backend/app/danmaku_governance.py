from __future__ import annotations

import hashlib
import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Iterable

from sqlalchemy.orm import Session

from .config import DATA_DIR
from .models import DanmakuComment, Drama, Episode


MODEL_VERSION = "danmaku-governance-v2-seven-layer"
SMALL_MODEL_PATH = DATA_DIR / "danmaku_small_model.json"

ABUSE_WORDS = ("傻逼", "垃圾", "滚", "死全家", "恶心死", "贱", "婊", "脑残")
AD_PATTERNS = (
    re.compile(r"\b\d{6,}\b"),
    re.compile(r"(微信|vx|qq|加群|私信|关注我|链接|http|www)", re.I),
)
SPOILER_HINTS = (
    "剧透",
    "结局",
    "大结局",
    "真相",
    "最后",
    "后来",
    "其实",
    "下一集",
    "看完回来",
    "答案是",
)
OUT_OF_CONTEXT_HINTS = (
    "抖音",
    "二刷",
    "三刷",
    "四刷",
    "回评",
    "来晚",
    "新剧",
    "上线",
    "粉丝",
    "收藏",
    "更新",
    "舔屏",
    "男频",
    "女频",
)
HIGH_VALUE_HINTS = ("哈哈", "破防", "心疼", "爽", "震惊", "好甜", "救命", "哭", "笑", "磕")
LOW_QUALITY_PATTERNS = (
    re.compile(r"^[\W_]+$"),
    re.compile(r"(.)(\1){8,}"),
)

TIME_AWARE_RULES = (
    {
        "drama_title": "北往",
        "episode_no": 1,
        "before_sec": 270,
        "keywords": ("摩托", "骑车", "骑摩托", "开回去", "到家了", "已经到家", "扒火车"),
        "suggested_time_sec": 270,
        "reason": "交通工具揭晓前不提前展示回家方式。",
    },
    {
        "drama_title": "那年冬至",
        "episode_no": 1,
        "before_sec": 110,
        "keywords": ("选第二", "第二个", "答案是", "会选"),
        "suggested_time_sec": 112,
        "reason": "竞猜答案出现前不提前透露选项结果。",
    },
    {
        "drama_title": "那年冬至",
        "episode_no": 1,
        "before_sec": 170,
        "keywords": ("亲了", "亲吻", "接吻", "亲嘴"),
        "suggested_time_sec": 170,
        "reason": "亲吻高光前不提前剧透情感反转。",
    },
)


@dataclass
class GovernanceResult:
    review_status: str
    mode: str
    risk_score: float
    quality_score: float
    spoiler_score: float
    relevance_score: float
    suggested_time_sec: float | None
    cluster_key: str
    cluster_size: int
    layers: dict
    reason: str


def clamp(value: float, low: float = 0, high: float = 1) -> float:
    return round(max(low, min(high, value)), 3)


def compact_text(text: str) -> str:
    return re.sub(r"\s+", "", text or "")


def cluster_key(text: str) -> str:
    normalized = re.sub(r"[\W_]+", "", (text or "").lower())
    normalized = re.sub(r"(哈){2,}", "哈哈", normalized)
    normalized = re.sub(r"(啊){2,}", "啊啊", normalized)
    normalized = re.sub(r"(！|!|？|\?)+", "", normalized)
    if not normalized:
        normalized = "empty"
    digest = hashlib.sha1(normalized.encode("utf-8")).hexdigest()[:12]
    return f"{normalized[:24]}_{digest}"


def rule_layer(text: str) -> dict:
    compact = compact_text(text)
    issues: list[str] = []
    risk = 0.0
    quality = 0.72

    if not compact:
        issues.append("空内容")
        risk += 1
        quality = 0
    if len(compact) > 40:
        issues.append("过长")
        risk += 0.25
        quality -= 0.18
    if any(word in compact for word in ABUSE_WORDS):
        issues.append("低俗/攻击词")
        risk += 0.9
        quality -= 0.5
    if any(pattern.search(compact) for pattern in AD_PATTERNS):
        issues.append("疑似广告或联系方式")
        risk += 0.8
        quality -= 0.45
    if any(pattern.search(compact) for pattern in LOW_QUALITY_PATTERNS):
        issues.append("纯符号或刷屏")
        risk += 0.45
        quality -= 0.35
    if any(hint in compact for hint in SPOILER_HINTS):
        issues.append("明显剧透词")
        risk += 0.45
    if any(hint in compact for hint in OUT_OF_CONTEXT_HINTS):
        issues.append("站外或粉丝语境")
        risk += 0.25
        quality -= 0.18

    return {"risk": clamp(risk), "quality": clamp(quality), "issues": issues}


def time_layer(text: str, episode: Episode, time_sec: float) -> dict:
    compact = compact_text(text)
    for rule in TIME_AWARE_RULES:
        if rule["drama_title"] not in episode.drama.title or int(rule["episode_no"]) != int(episode.episode_no):
            continue
        if float(time_sec) >= float(rule["before_sec"]):
            continue
        if any(keyword in compact for keyword in rule["keywords"]):
            return {
                "spoiler": 0.95,
                "suggested_time_sec": float(rule["suggested_time_sec"]),
                "issues": [rule["reason"]],
            }
    return {"spoiler": 0.0, "suggested_time_sec": None, "issues": []}


def semantic_layer_local(text: str, episode: Episode) -> dict:
    compact = compact_text(text)
    relevance = 0.68
    quality = 0.7
    issues: list[str] = []

    drama_tokens = [token for token in re.split(r"[：:，,、\s]+", episode.drama.title) if len(token) >= 2]
    if any(token in compact for token in drama_tokens):
        relevance += 0.12
    if any(hint in compact for hint in HIGH_VALUE_HINTS):
        quality += 0.12
    if any(hint in compact for hint in OUT_OF_CONTEXT_HINTS):
        relevance -= 0.2
        issues.append("语义上偏站外评论")
    if re.search(r"[\u4e00-\u9fff]", compact) is None:
        relevance -= 0.25
        issues.append("缺少可理解语义")

    return {"relevance": clamp(relevance), "quality": clamp(quality), "issues": issues, "provider": "local"}


def load_small_model() -> dict:
    if not SMALL_MODEL_PATH.exists():
        return {"positive": {}, "negative": {}, "version": "small-model-default"}
    try:
        return json.loads(SMALL_MODEL_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {"positive": {}, "negative": {}, "version": "small-model-broken"}


def small_model_layer(text: str, model: dict | None = None) -> dict:
    model = model or load_small_model()
    compact = compact_text(text)
    positive = model.get("positive", {})
    negative = model.get("negative", {})
    score = 0.5
    for token, weight in positive.items():
        if token and token in compact:
            score += float(weight)
    for token, weight in negative.items():
        if token and token in compact:
            score -= float(weight)
    pass_score = clamp(score)
    return {
        "pass_score": pass_score,
        "confidence": clamp(abs(pass_score - 0.5) * 2),
        "model_version": model.get("version", "small-model-default"),
    }


def llm_review_layer(rule: dict, time: dict, semantic: dict, small: dict, cluster_size: int, like_count: int) -> dict:
    reasons: list[str] = []
    medium_rule_risk = 0.28 <= float(rule["risk"]) < 0.85
    time_risk = float(time["spoiler"]) >= 0.4
    low_relevance = float(semantic["relevance"]) < 0.5
    low_confidence = float(small.get("confidence", 0)) < 0.22
    high_value = cluster_size >= 2 or like_count >= 8
    if medium_rule_risk:
        reasons.append("规则风险中等")
    if time_risk:
        reasons.append("疑似时间点剧透")
    if low_relevance:
        reasons.append("语义相关度偏低")
    if low_confidence and high_value:
        reasons.append("重复/高赞且小模型置信度低")
    candidate = bool(reasons)
    return {
        "candidate": candidate,
        "provider": "offline_llm_batch",
        "action": "queue_for_llm_review" if candidate else "skip",
        "issues": reasons,
    }


def decide_status(rule: dict, time: dict, semantic: dict, small: dict, cluster_size: int, like_count: int) -> str:
    risk = rule["risk"]
    spoiler = time["spoiler"]
    relevance = semantic["relevance"]
    quality = min(rule["quality"], semantic["quality"])
    small_score = small["pass_score"]
    high_value = like_count >= 8 or cluster_size >= 10

    if risk >= 0.85 or quality <= 0.25:
        return "hidden"
    if spoiler >= 0.75:
        return "needs_review"
    if relevance < 0.38 or small_score < 0.3:
        return "needs_review"
    if high_value:
        return "needs_review"
    return "approved"


def evaluate_comment(comment: DanmakuComment, cluster_size: int, model: dict | None = None) -> GovernanceResult:
    episode = comment.episode
    rule = rule_layer(comment.text)
    time = time_layer(comment.text, episode, float(comment.time_sec or 0))
    semantic = semantic_layer_local(comment.text, episode)
    small = small_model_layer(comment.text, model)
    llm_review = llm_review_layer(
        rule,
        time,
        semantic,
        small,
        cluster_size,
        int(comment.source_like_count or 0),
    )
    key = cluster_key(comment.text)
    status = decide_status(rule, time, semantic, small, cluster_size, int(comment.source_like_count or 0))
    mode = comment.mode if comment.mode in {"light", "carnival", "curated", "seed"} else "light"
    if status == "approved" and (rule["quality"] >= 0.78 or semantic["quality"] >= 0.8):
        mode = "carnival" if mode != "light" else mode

    risk_score = clamp(max(rule["risk"], 1 - small["pass_score"]))
    quality_score = clamp(min(rule["quality"], semantic["quality"]))
    spoiler_score = clamp(time["spoiler"])
    relevance_score = clamp(semantic["relevance"])
    all_issues = [*rule["issues"], *time["issues"], *semantic["issues"]]
    layers = {
        "rule": rule,
        "time_aware": time,
        "semantic": semantic,
        "cluster": {"cluster_key": key, "cluster_size": cluster_size},
        "small_model": small,
        "llm_review": llm_review,
        "human_review": {"required": status == "needs_review"},
    }
    return GovernanceResult(
        review_status=status,
        mode=mode,
        risk_score=risk_score,
        quality_score=quality_score,
        spoiler_score=spoiler_score,
        relevance_score=relevance_score,
        suggested_time_sec=time["suggested_time_sec"],
        cluster_key=key,
        cluster_size=cluster_size,
        layers=layers,
        reason="；".join(all_issues) or "通过分层治理",
    )


def build_cluster_sizes(comments: Iterable[DanmakuComment]) -> Counter[str]:
    counter: Counter[str] = Counter()
    for comment in comments:
        counter[cluster_key(comment.text)] += 1
    return counter


def apply_governance_to_episode(db: Session, episode_id: int | None = None) -> dict:
    query = db.query(DanmakuComment).join(Episode).join(Drama)
    if episode_id:
        query = query.filter(DanmakuComment.episode_id == episode_id)
    comments = query.order_by(DanmakuComment.episode_id.asc(), DanmakuComment.time_sec.asc()).all()
    cluster_sizes = build_cluster_sizes(comments)
    model = load_small_model()
    summary = defaultdict(int)
    for comment in comments:
        result = evaluate_comment(comment, cluster_sizes[cluster_key(comment.text)], model)
        comment.review_status = result.review_status
        comment.mode = result.mode
        comment.risk_score = result.risk_score
        comment.quality_score = result.quality_score
        comment.spoiler_score = result.spoiler_score
        comment.relevance_score = result.relevance_score
        comment.cluster_key = result.cluster_key
        comment.cluster_size = result.cluster_size
        comment.suggested_time_sec = result.suggested_time_sec
        comment.moderation_model_version = MODEL_VERSION
        comment.moderation_layers_json = json.dumps(result.layers, ensure_ascii=False)
        comment.moderation_reason = result.reason
        summary[result.review_status] += 1
    db.commit()
    return {"processed": len(comments), "summary": dict(summary), "model_version": MODEL_VERSION}


def train_small_model_from_db(db: Session, output_path: Path = SMALL_MODEL_PATH) -> dict:
    rows = db.query(DanmakuComment).all()
    positive: Counter[str] = Counter()
    negative: Counter[str] = Counter()
    token_re = re.compile(r"[\u4e00-\u9fff]{1,4}|[A-Za-z0-9]{2,}")
    for row in rows:
        tokens = set(token_re.findall(compact_text(row.text)))
        target = positive if row.review_status == "approved" else negative
        for token in tokens:
            target[token] += 1

    pos_total = max(1, sum(positive.values()))
    neg_total = max(1, sum(negative.values()))
    payload = {
        "version": "danmaku-small-model-v1",
        "positive": {
            token: round(min(0.12, count / pos_total * 12), 4)
            for token, count in positive.most_common(80)
            if count >= 2
        },
        "negative": {
            token: round(min(0.16, count / neg_total * 12), 4)
            for token, count in negative.most_common(80)
            if count >= 2
        },
        "trained_rows": len(rows),
        "updated_at": datetime.utcnow().isoformat(timespec="seconds") + "Z",
        "training_source": "danmaku_comments.review_status",
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return payload
