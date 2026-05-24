from dataclasses import dataclass


ABUSE_KEYWORDS = {
    "傻逼",
    "垃圾",
    "滚",
    "死全家",
    "恶心死",
}

GENERAL_SPOILER_PATTERNS = (
    "剧透",
    "后面会",
    "下一集他",
    "下一集她",
    "结局是",
    "最后他",
    "最后她",
    "其实他",
    "其实她",
    "真相是",
)

EPISODE_SPOILER_RULES = [
    {
        "episode_id": 3,
        "before_sec": 270,
        "keywords": ("摩托", "摩托车", "骑车回", "摇滚车"),
        "message": "交通工具揭晓前不要提前剧透。",
    }
]


@dataclass
class ModerationResult:
    allowed: bool
    text: str
    category: str = "ok"
    message: str = ""


def normalize_text(text: str) -> str:
    return " ".join(text.strip().split())


def moderate_danmaku(text: str, episode_id: int, time_sec: float) -> ModerationResult:
    normalized = normalize_text(text)
    compact = normalized.replace(" ", "")
    if not normalized:
        return ModerationResult(False, normalized, "empty", "弹幕不能为空。")

    for keyword in ABUSE_KEYWORDS:
        if keyword in compact:
            return ModerationResult(False, normalized, "blacklist", "弹幕包含不友好表达，已拦截。")

    for pattern in GENERAL_SPOILER_PATTERNS:
        if pattern in compact:
            return ModerationResult(False, normalized, "spoiler", "弹幕疑似剧透，请换一种不破坏观看体验的说法。")

    for rule in EPISODE_SPOILER_RULES:
        if episode_id != rule["episode_id"] or time_sec >= rule["before_sec"]:
            continue
        if any(keyword in compact for keyword in rule["keywords"]):
            return ModerationResult(False, normalized, "spoiler", rule["message"])

    return ModerationResult(True, normalized)


def moderation_rules_payload() -> dict:
    return {
        "max_length": 40,
        "rules": [
            {"category": "blacklist", "description": "拦截辱骂、人身攻击和明显不友好表达。"},
            {"category": "spoiler", "description": "拦截提前透露结局、身份、交通工具等关键剧情的信息。"},
            {"category": "time_aware", "description": "部分弹幕会根据当前播放时间判断是否剧透。"},
        ],
    }
