from __future__ import annotations


HIGHLIGHT_TAXONOMY = [
    {
        "key": "conflict",
        "label": "冲突对抗",
        "description": "角色之间的正面对峙、争吵、打斗、立场冲突。",
        "interaction": "站队投票",
        "aliases": ["冲突", "对抗", "争执", "打斗", "冲突对抗"],
        "emotions": ["愤怒", "紧张", "站队"],
    },
    {
        "key": "reveal",
        "label": "反转揭秘",
        "description": "身份反转、真相揭露、误会解开、意外暴露。",
        "interaction": "震惊反应",
        "aliases": ["反转", "揭秘", "真相", "反转揭秘"],
        "emotions": ["震惊", "意外", "恍然大悟"],
    },
    {
        "key": "power",
        "label": "爽点逆袭",
        "description": "打脸、翻盘、强者出手、压制反派、处境逆转。",
        "interaction": "爽值连击",
        "aliases": ["爽点", "逆袭", "打脸", "高能名场面", "名场面", "爽点逆袭"],
        "emotions": ["爽", "解气", "燃"],
    },
    {
        "key": "sweet",
        "label": "甜蜜心动",
        "description": "撒糖、守护、暧昧、温柔互动、关系升温。",
        "interaction": "心动反应",
        "aliases": ["甜蜜", "撒糖", "心动", "甜蜜心动"],
        "emotions": ["心动", "磕到了", "甜"],
    },
    {
        "key": "tear",
        "label": "虐心共情",
        "description": "离别、牺牲、委屈、误解、崩溃、悲伤感动。",
        "interaction": "情绪共情卡",
        "aliases": ["虐点", "悲伤感动", "虐心", "共情", "虐心共情"],
        "emotions": ["心疼", "难过", "破防"],
    },
    {
        "key": "suspense",
        "label": "悬念钩子",
        "description": "关键线索、危机未解、结尾吊胃口、下一集钩子。",
        "interaction": "剧情预测",
        "aliases": ["悬念", "悬疑反转", "线索", "钩子", "悬念钩子"],
        "emotions": ["期待", "好奇", "紧张"],
    },
    {
        "key": "comedy",
        "label": "搞笑解压",
        "description": "反差笑点、误会喜剧、夸张表演、轻松吐槽。",
        "interaction": "哈哈弹幕",
        "aliases": ["搞笑", "笑点", "喜剧", "搞笑解压"],
        "emotions": ["好笑", "离谱", "欢乐"],
    },
    {
        "key": "danger",
        "label": "危机紧张",
        "description": "追杀、倒计时、危险逼近、生死关头、高压威胁。",
        "interaction": "紧张值",
        "aliases": ["危机", "紧张", "危险", "危机紧张"],
        "emotions": ["紧张", "担心", "屏息"],
    },
]

HIGHLIGHT_TYPE_LABELS = {item["label"] for item in HIGHLIGHT_TAXONOMY}
HIGHLIGHT_TYPE_ALIASES = {
    alias: item["label"]
    for item in HIGHLIGHT_TAXONOMY
    for alias in [item["label"], *item["aliases"]]
}
ALLOWED_EMOTIONS = {
    "爽",
    "震惊",
    "心疼",
    "愤怒",
    "好笑",
    "心动",
    "紧张",
    "期待",
    "站队",
    "意外",
    "恍然大悟",
    "解气",
    "燃",
    "磕到了",
    "甜",
    "难过",
    "破防",
    "好奇",
    "离谱",
    "欢乐",
    "担心",
    "屏息",
}

MAX_HIGHLIGHTS_PER_EPISODE = 5
MIN_HIGHLIGHT_GAP_SEC = 25


def normalize_highlight_type(value: str) -> str:
    return HIGHLIGHT_TYPE_ALIASES.get(value, value)


def taxonomy_payload() -> list[dict]:
    return [
        {
            "key": item["key"],
            "label": item["label"],
            "description": item["description"],
            "interaction": item["interaction"],
            "aliases": item["aliases"],
            "emotions": item["emotions"],
        }
        for item in HIGHLIGHT_TAXONOMY
    ]
