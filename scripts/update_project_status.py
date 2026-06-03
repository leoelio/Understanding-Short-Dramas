import argparse
import sqlite3
import subprocess
from collections import Counter
from datetime import datetime
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "data" / "app.db"
STATUS_PATH = ROOT / "docs" / "PROJECT_STATUS.md"
RUN_LOG_PATH = ROOT / "docs" / "RUN_LOG.md"


def run_git(args: list[str]) -> str:
    result = subprocess.run(
        ["git", "-c", f"safe.directory={ROOT.as_posix()}", *args],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    return result.stdout.strip() or result.stderr.strip()


def scalar(connection: sqlite3.Connection, query: str) -> int:
    try:
        row = connection.execute(query).fetchone()
        return int(row[0] or 0) if row else 0
    except sqlite3.Error:
        return 0


def db_snapshot() -> dict:
    if not DB_PATH.exists():
        return {
            "exists": False,
            "dramas": 0,
            "episodes": 0,
            "highlights": 0,
            "interactions": 0,
            "danmaku": 0,
            "experience_configs": 0,
            "ai_remixes": 0,
            "social_posts": 0,
            "social_comments": 0,
            "social_notifications": 0,
            "reviewed_episodes": 0,
            "pending_episodes": 0,
            "sources": {},
        }

    with sqlite3.connect(DB_PATH) as connection:
        sources = Counter(
            {
                source or "unknown": count
                for source, count in connection.execute(
                    "select source, count(*) from highlights group by source"
                ).fetchall()
            }
        )
        reviewed = scalar(
            connection,
            """
            select count(distinct episode_id)
            from highlights
            where source = 'human_review'
            """,
        )
        episodes = scalar(connection, "select count(*) from episodes")
        return {
            "exists": True,
            "dramas": scalar(connection, "select count(*) from dramas"),
            "episodes": episodes,
            "highlights": scalar(connection, "select count(*) from highlights"),
            "interactions": scalar(connection, "select count(*) from interactions"),
            "danmaku": scalar(connection, "select count(*) from danmaku_comments"),
            "experience_configs": scalar(connection, "select count(*) from episode_experience_configs"),
            "ai_remixes": scalar(connection, "select count(*) from episode_ai_remixes"),
            "social_posts": scalar(connection, "select count(*) from social_posts"),
            "social_comments": scalar(connection, "select count(*) from social_comments where is_deleted = 0"),
            "social_notifications": scalar(connection, "select count(*) from social_notifications"),
            "reviewed_episodes": reviewed,
            "pending_episodes": max(0, episodes - reviewed),
            "sources": dict(sources),
        }


def list_changed_files() -> list[str]:
    output = run_git(["status", "--short"])
    return [line.strip() for line in output.splitlines() if line.strip()]


def render_status(args: argparse.Namespace, snapshot: dict, now: str) -> str:
    branch = run_git(["branch", "--show-current"])
    commit = run_git(["rev-parse", "--short", "HEAD"])
    remote = run_git(["remote", "get-url", "origin"])
    changed_files = list_changed_files()
    source_lines = "\n".join(
        f"- `{source}`: {count}" for source, count in sorted(snapshot["sources"].items())
    ) or "- 暂无"
    changed_lines = "\n".join(f"- `{item}`" for item in changed_files) or "- 工作区干净"
    change_lines = "\n".join(f"- {item}" for item in args.change) or "- 本次未填写变更摘要"
    next_lines = "\n".join(f"- {item}" for item in args.next) or "- 继续按产品负责人确认的优先级推进"

    return f"""# Project Status

更新时间：{now}

## 当前目标

{args.summary}

## Git 状态

- 分支：`{branch}`
- 最新提交：`{commit}`
- 远端：`{remote}`
- 工作区：
{changed_lines}

## 数据状态

- 数据库存在：{"是" if snapshot["exists"] else "否"}
- 短剧：{snapshot["dramas"]}
- 剧集：{snapshot["episodes"]}
- 高光点：{snapshot["highlights"]}
- 已复核剧集：{snapshot["reviewed_episodes"]}
- 待复核剧集：{snapshot["pending_episodes"]}
- 互动记录：{snapshot["interactions"]}
- 弹幕记录：{snapshot["danmaku"]}
- 体验配置：{snapshot["experience_configs"]}
- 片尾 AI 二创：{snapshot["ai_remixes"]}
- 社交动态：{snapshot["social_posts"]}
- 社交评论：{snapshot["social_comments"]}
- 社交通知：{snapshot["social_notifications"]}

## 高光来源

{source_lines}

## 已完成能力

- 移动端 Web 短剧列表、播放页、剧集切换。
- 高光时间轴下发、按播放时间触发互动组件。
- 互动点击上报、选项占比和后台统计。
- 大模型离线标注链路、人工复核工作台、复核进度筛选。
- 8 类高光分类体系和稀疏高光规则。
- 弹幕评论、三种弹幕模式和弹幕样式设置。
- 分类型高光动效：冲突站队、反转狂点、爽点连击、甜蜜气泡、虐心共情、悬念线索、搞笑贴纸、危机心跳。
- 体验配置复核台：服务端存储播放器主题、贴图时间轴、弹幕策略、来源和版本。
- 片尾 AI 二创保底版：剧情预测选项、文字卡、三格分镜、生成记录和精选管理。
- 社交 MVP：聊聊消息红点、逛逛动态发布、公开/好友/仅自己权限、点赞评论和基础内容审核。

## 本次变更摘要

{change_lines}

## 下一步建议

{next_lines}

## 安全提醒

- `.env`、视频素材、PDF/DOCX 原始资料不进入 Git。
- 不在文档、提交信息或日志中写入任何模型 API Key、接入点密钥或私密素材内容。
"""


def append_run_log(args: argparse.Namespace, snapshot: dict, now: str) -> None:
    change_lines = "\n".join(f"  - {item}" for item in args.change) or "  - 本次未填写变更摘要"
    next_lines = "\n".join(f"  - {item}" for item in args.next) or "  - 继续按产品负责人确认的优先级推进"
    entry = f"""
## {now}

- 目标：{args.summary}
- Git：`{run_git(["rev-parse", "--short", "HEAD"])}` / `{run_git(["branch", "--show-current"])}`
- 数据：{snapshot["dramas"]} 部短剧，{snapshot["episodes"]} 集，{snapshot["highlights"]} 个高光，{snapshot["reviewed_episodes"]} 集已复核，{snapshot["danmaku"]} 条弹幕，{snapshot["experience_configs"]} 条体验配置，{snapshot["ai_remixes"]} 条片尾 AI 二创，{snapshot["social_posts"]} 条社交动态。
- 变更：
{change_lines}
- 下一步：
{next_lines}
"""
    if not RUN_LOG_PATH.exists():
        RUN_LOG_PATH.write_text("# Run Log\n", encoding="utf-8")
    with RUN_LOG_PATH.open("a", encoding="utf-8") as handle:
        handle.write(entry)


def main() -> None:
    parser = argparse.ArgumentParser(description="更新项目状态快照，并追加本轮运行记录。")
    parser.add_argument("--summary", required=True, help="本轮工作的目标或当前状态摘要。")
    parser.add_argument("--change", action="append", default=[], help="本轮完成的变更，可重复传入。")
    parser.add_argument("--next", action="append", default=[], help="下一步建议，可重复传入。")
    parser.add_argument("--no-append", action="store_true", help="只更新 PROJECT_STATUS.md，不追加 RUN_LOG.md。")
    args = parser.parse_args()

    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    snapshot = db_snapshot()
    STATUS_PATH.write_text(render_status(args, snapshot, now), encoding="utf-8")
    if not args.no_append:
        append_run_log(args, snapshot, now)
    print(STATUS_PATH)
    if not args.no_append:
        print(RUN_LOG_PATH)


if __name__ == "__main__":
    main()
