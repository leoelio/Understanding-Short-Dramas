from __future__ import annotations

import csv
import json
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.database import SessionLocal  # noqa: E402
from backend.app.danmaku_governance import apply_governance_to_episode  # noqa: E402
from backend.app.migrations import ensure_database_schema  # noqa: E402
from backend.app.models import DanmakuComment, Drama, Episode  # noqa: E402


CSV_PATH = ROOT / "圈选剧前5集弹幕.csv"
DANMAKU_FIXTURE_PATH = ROOT / "backend" / "app" / "fixtures" / "danmaku_comments.json"
CHAT_EMOJI_PACK_PATH = ROOT / "frontend" / "assets" / "chat_emoji_pack.json"

EPISODE_RE = re.compile(r"第\s*(\d+)\s*集")
BRACKET_TOKEN_RE = re.compile(r"\[([^\[\]]{1,8})\]")

TOKEN_EMOJI = {
    "送心": "💗",
    "爱慕": "😍",
    "大笑": "😆",
    "笑": "😄",
    "笑哭": "😂",
    "捂脸": "🤦",
    "偷笑": "🤭",
    "哭": "😭",
    "流泪": "🥲",
    "泪奔": "😭",
    "赞": "👍",
    "鼓掌": "👏",
    "送花": "🌸",
    "没看够": "👀",
    "爽": "🔥",
    "惊讶": "😳",
    "震惊": "😱",
    "疑问": "❓",
    "发怒": "😤",
    "心碎": "💔",
    "玫瑰": "🌹",
    "加油": "💪",
}

BASE_EMOJI_PACKS = [
    {
        "id": "hot",
        "title": "追剧热梗",
        "items": [
            {"id": "laugh", "label": "大笑", "icon": "😆", "text": "😆 哈哈哈"},
            {"id": "shock", "label": "震惊", "icon": "😱", "text": "😱 这也太突然了"},
            {"id": "heart", "label": "送心", "icon": "💗", "text": "💗 磕到了"},
            {"id": "cover-face", "label": "捂脸", "icon": "🤦", "text": "🤦 救命太真实了"},
            {"id": "question", "label": "疑问", "icon": "❓", "text": "❓ 到底怎么回事"},
            {"id": "strong", "label": "加油", "icon": "💪", "text": "💪 冲啊"},
        ],
    },
    {
        "id": "mood",
        "title": "情绪表达",
        "items": [
            {"id": "hurt", "label": "心疼", "icon": "🥲", "text": "🥲 心疼了"},
            {"id": "cry", "label": "破防", "icon": "😭", "text": "😭 破防了"},
            {"id": "sweet", "label": "心动", "icon": "😍", "text": "😍 有点心动"},
            {"id": "angry", "label": "生气", "icon": "😤", "text": "😤 气到了"},
            {"id": "wow", "label": "哇塞", "icon": "✨", "text": "✨ 哇塞"},
            {"id": "applause", "label": "鼓掌", "icon": "👏", "text": "👏 这段可以"},
        ],
    },
    {
        "id": "kaomoji",
        "title": "颜文字",
        "items": [
            {"id": "kao-happy", "label": "开心", "icon": "(*≧▽≦)", "text": "(*≧▽≦)"},
            {"id": "kao-love", "label": "喜欢", "icon": "(｡･ω･｡)ﾉ♡", "text": "(｡･ω･｡)ﾉ♡"},
            {"id": "kao-cry", "label": "哭哭", "icon": "QAQ", "text": "QAQ"},
            {"id": "kao-go", "label": "冲", "icon": "(ง •̀_•́)ง", "text": "(ง •̀_•́)ง 冲"},
        ],
    },
]

ABUSE_WORDS = (
    "傻逼",
    "垃圾",
    "滚",
    "死全家",
    "恶心死",
    "贱",
    "婊",
)

SPOILER_HINTS = (
    "剧透",
    "结局",
    "大结局",
    "看完回来",
    "看完回来的",
    "真相是",
    "最后他",
    "最后她",
    "后来他",
    "后来她",
    "其实他",
    "其实她",
    "后面他",
    "后面她",
    "下一集他",
    "下一集她",
)

META_HINTS = (
    "抖音",
    "长剧",
    "收藏",
    "粉丝",
    "更新",
    "划走",
    "二刷",
    "三刷",
    "刷了",
    "回评",
    "来晚",
    "来啦",
    "我来了",
    "终于",
    "新剧",
    "上线",
    "连续看",
    "追追追",
    "一追到底",
    "好看",
    "好剧本",
    "实力派",
    "梦幻联动",
    "联动",
    "男帅女美",
    "画质",
    "壁纸",
    "舔屏",
    "翅宝",
    "翅翅",
    "苏苏",
    "京烁",
    "好看好看",
    "超级好看",
    "太帅",
    "好帅",
    "好美",
    "男频",
    "女频",
    "演员",
)

TIME_AWARE_SPOILER_RULES = (
    {
        "drama_title": "北往",
        "episode_no": 1,
        "before_sec": 270,
        "keywords": ("摩托", "骑车", "骑摩托", "开回去", "已经到家", "到家了", "扒火车", "火车回家"),
    },
    {
        "drama_title": "那年冬至",
        "episode_no": 1,
        "before_sec": 110,
        "keywords": ("选第二", "第二个", "答案是", "会选"),
    },
    {
        "drama_title": "那年冬至",
        "episode_no": 1,
        "before_sec": 170,
        "keywords": ("亲了", "亲吻", "接吻", "亲嘴"),
    },
)

INTENSE_HINTS = (
    "哈哈",
    "笑",
    "啊啊",
    "救命",
    "震惊",
    "卧槽",
    "牛",
    "爽",
    "磕",
    "亲",
    "心动",
    "破防",
    "心疼",
    "？？",
    "!!",
    "！！",
)


@dataclass
class Candidate:
    time_sec: float
    text: str
    original_text: str
    mode: str
    score: int
    likes: int


def parse_episode_no(value: str) -> int | None:
    match = EPISODE_RE.search(value or "")
    return int(match.group(1)) if match else None


def is_bad_text(text: str, drama_title: str, episode_no: int, time_sec: float) -> bool:
    compact = "".join(text.split())
    if len(compact) < 2 or len(compact) > 48:
        return True
    if not re.search(r"[\u4e00-\u9fffA-Za-z0-9]", compact):
        return True
    if any(word in compact for word in ABUSE_WORDS):
        return True
    if any(hint in compact for hint in SPOILER_HINTS):
        return True
    if any(hint in compact for hint in META_HINTS):
        return True
    if re.search(r"\d+\s*集", compact):
        return True
    if re.search(r"([一二三四五六七八九十\d]+)刷", compact):
        return True
    for rule in TIME_AWARE_SPOILER_RULES:
        if rule["drama_title"] != drama_title or rule["episode_no"] != episode_no:
            continue
        if time_sec < float(rule["before_sec"]) and any(keyword in compact for keyword in rule["keywords"]):
            return True
    return False


def render_tokens(text: str, token_counter: Counter[str]) -> str:
    def replace(match: re.Match[str]) -> str:
        token = match.group(1).strip()
        token_counter[token] += 1
        return TOKEN_EMOJI.get(token, match.group(0))

    return BRACKET_TOKEN_RE.sub(replace, text)


def normalize_text(raw: str, token_counter: Counter[str]) -> str:
    text = render_tokens(raw.strip(), token_counter)
    text = re.sub(r"\s+", " ", text).strip()
    if len(text) > 40:
        text = text[:39] + "…"
    return text


def classify_mode(text: str, likes: int, raw: str) -> tuple[str, int]:
    compact = text.replace(" ", "")
    token_count = len(BRACKET_TOKEN_RE.findall(raw))
    score = likes + token_count * 2
    score += sum(2 for hint in INTENSE_HINTS if hint in compact)
    score += compact.count("！") + compact.count("?") + compact.count("？")
    return ("carnival" if score >= 3 else "light", score)


def select_spread(candidates: list[Candidate], target: int, min_gap: float) -> list[Candidate]:
    if not candidates or target <= 0:
        return []

    selected: list[Candidate] = []
    used_text: set[str] = set()
    for item in sorted(candidates, key=lambda row: (row.time_sec, -row.score)):
        if item.text in used_text:
            continue
        if selected and item.time_sec - selected[-1].time_sec < min_gap:
            continue
        selected.append(item)
        used_text.add(item.text)
        if len(selected) >= target:
            return selected

    if len(selected) >= target:
        return selected

    selected_keys = {(row.time_sec, row.text) for row in selected}
    for item in sorted(candidates, key=lambda row: (-row.score, row.time_sec)):
        if item.text in used_text or (item.time_sec, item.text) in selected_keys:
            continue
        selected.append(item)
        used_text.add(item.text)
        if len(selected) >= target:
            break
    return sorted(selected, key=lambda row: row.time_sec)


def target_counts(duration: float) -> tuple[int, int]:
    total = int(min(110, max(36, duration / 3.2)))
    light = int(total * 0.58)
    return light, total - light


def build_emoji_pack(token_counter: Counter[str]) -> dict:
    csv_items = []
    for token, count in token_counter.most_common(18):
        icon = TOKEN_EMOJI.get(token)
        if not icon:
            continue
        csv_items.append(
            {
                "id": f"csv-{token}",
                "label": token,
                "icon": icon,
                "text": f"{icon} {token}",
                "source_count": count,
            }
        )

    packs = [*BASE_EMOJI_PACKS]
    if csv_items:
        packs.insert(0, {"id": "csv-hot", "title": "弹幕常用", "items": csv_items})
    return {"version": 1, "source": "圈选剧前5集弹幕.csv", "packs": packs}


def main() -> None:
    ensure_database_schema()
    if not CSV_PATH.exists():
        raise SystemExit(f"CSV not found: {CSV_PATH}")

    governance = {}
    db = SessionLocal()
    try:
        episodes = {
            (drama.title, episode.episode_no): episode
            for drama in db.query(Drama).all()
            for episode in drama.episodes
        }
        durations = {(episode.drama.title, episode.episode_no): float(episode.duration_sec or 0) for episode in db.query(Episode)}
    finally:
        db.close()

    token_counter: Counter[str] = Counter()
    grouped: dict[tuple[str, int], list[Candidate]] = defaultdict(list)
    csv_dramas: Counter[str] = Counter()
    imported_rows = 0
    skipped_rows = 0

    with CSV_PATH.open("r", encoding="gb18030", newline="") as file:
        reader = csv.DictReader(file)
        for row in reader:
            drama_title = row.get("剧名称", "").strip()
            episode_no = parse_episode_no(row.get("group_title", ""))
            csv_dramas[drama_title] += 1
            if episode_no is None or (drama_title, episode_no) not in episodes:
                skipped_rows += 1
                continue

            try:
                time_sec = round(float(row["发弹幕时刻相对于视频起始时间偏移量"]) / 1000, 2)
                likes = int(float(row.get("累计点赞数") or 0))
            except ValueError:
                skipped_rows += 1
                continue

            duration = durations.get((drama_title, episode_no), 0)
            if duration and (time_sec < 0 or time_sec > duration + 1.5):
                skipped_rows += 1
                continue

            raw_text = row.get("弹幕内容", "")
            text = normalize_text(raw_text, token_counter)
            if is_bad_text(text, drama_title, episode_no, time_sec):
                skipped_rows += 1
                continue

            mode, score = classify_mode(text, likes, raw_text)
            grouped[(drama_title, episode_no)].append(
                Candidate(time_sec=time_sec, text=text, original_text=raw_text.strip(), mode=mode, score=score, likes=likes)
            )
            imported_rows += 1

    fixtures = []
    for drama_title, episode_no in sorted(grouped):
        duration = durations.get((drama_title, episode_no), 0)
        light_target, carnival_target = target_counts(duration)
        light = select_spread([row for row in grouped[(drama_title, episode_no)] if row.mode == "light"], light_target, 2.1)
        carnival = select_spread(
            [row for row in grouped[(drama_title, episode_no)] if row.mode == "carnival"], carnival_target, 1.2
        )
        comments = sorted(light + carnival, key=lambda row: (row.time_sec, row.mode))
        fixtures.append(
            {
                "drama_title": drama_title,
                "episode_no": episode_no,
                "comments": [
                    {
                        "time_sec": row.time_sec,
                        "text": row.text,
                        "original_text": row.original_text,
                        "source_like_count": row.likes,
                        "mode": row.mode,
                    }
                    for row in comments
                ],
            }
        )

    DANMAKU_FIXTURE_PATH.write_text(
        json.dumps(
            {
                "source": str(CSV_PATH.name),
                "encoding": "gb18030",
                "matched_episode_count": len(fixtures),
                "episodes": fixtures,
            },
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )
    CHAT_EMOJI_PACK_PATH.write_text(
        json.dumps(build_emoji_pack(token_counter), ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    db = SessionLocal()
    try:
        db.query(DanmakuComment).filter(DanmakuComment.session_id == "fixture").delete(synchronize_session=False)
        for fixture in fixtures:
            episode = episodes[(fixture["drama_title"], fixture["episode_no"])]
            for item in fixture["comments"]:
                db.add(
                    DanmakuComment(
                        episode_id=episode.id,
                        time_sec=float(item["time_sec"]),
                        text=item["text"],
                        original_text=item.get("original_text") or item["text"],
                        source_like_count=int(item.get("source_like_count") or 0),
                        mode=item["mode"],
                        session_id="fixture",
                    )
                )
        db.commit()
        governance = apply_governance_to_episode(db)
    finally:
        db.close()

    total_comments = sum(len(item["comments"]) for item in fixtures)
    missing_dramas = sorted(set(csv_dramas) - {key[0] for key in grouped})
    print(
        json.dumps(
            {
                "csv_rows_usable_before_sampling": imported_rows,
                "csv_rows_skipped": skipped_rows,
                "fixture_episodes": len(fixtures),
                "fixture_comments": total_comments,
                "governance": governance,
                "top_emoji_tokens": token_counter.most_common(12),
                "csv_dramas": len(csv_dramas),
                "csv_dramas_not_in_current_db": missing_dramas,
            },
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
