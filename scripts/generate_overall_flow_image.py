# -*- coding: utf-8 -*-
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


OUT = Path("docs/overall_flow_web_android_feishu.png")
W, H = 3000, 2100

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\simhei.ttf",
    r"C:\Windows\Fonts\simsun.ttc",
]
FONT_PATH = next((p for p in FONT_CANDIDATES if Path(p).exists()), None)


def font(size: int, bold: bool = False):
    if FONT_PATH:
        return ImageFont.truetype(FONT_PATH, size)
    return ImageFont.load_default()


img = Image.new("RGB", (W, H), "#f5f7fb")
d = ImageDraw.Draw(img)

TITLE = font(58)
SUB = font(30)
SECTION = font(30)
TEXT = font(28)
SMALL = font(23)
TINY = font(21)

PALETTE = {
    "source": ("#e8f0ff", "#2f66dd"),
    "ai": ("#f4ecff", "#8a3ffc"),
    "review": ("#fff7e8", "#d88a00"),
    "server": ("#e7f8f0", "#0f9f6e"),
    "web": ("#fff2df", "#f08c00"),
    "android": ("#eaf5ff", "#1683d8"),
    "voice": ("#fff0f6", "#d6336c"),
    "danmaku": ("#ecfdf3", "#16a34a"),
    "feedback": ("#fff8d9", "#ca8a04"),
    "line": ("#e8edf5", "#708199"),
}


def text_size(text: str, fnt) -> tuple[int, int]:
    box = d.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


def wrap(text: str, max_width: int, fnt) -> list[str]:
    lines: list[str] = []
    for raw in text.split("\n"):
        current = ""
        for ch in raw:
            candidate = current + ch
            if text_size(candidate, fnt)[0] <= max_width:
                current = candidate
            else:
                if current:
                    lines.append(current)
                current = ch
        if current:
            lines.append(current)
    return lines


def rounded_rect(x: int, y: int, w: int, h: int, fill: str, stroke: str, width: int = 4, radius: int = 22):
    d.rounded_rectangle((x, y, x + w, y + h), radius=radius, fill=fill, outline=stroke, width=width)


def card(
    x: int,
    y: int,
    w: int,
    h: int,
    title: str,
    subtitle: str,
    kind: str,
    title_font=TEXT,
    subtitle_font=SMALL,
):
    fill, stroke = PALETTE[kind]
    rounded_rect(x, y, w, h, fill, stroke)
    title_lines = wrap(title, w - 56, title_font)
    sub_lines = wrap(subtitle, w - 56, subtitle_font)
    total_h = len(title_lines) * 36 + len(sub_lines) * 30 + (10 if sub_lines else 0)
    cy = y + (h - total_h) // 2
    for line in title_lines:
        tw, th = text_size(line, title_font)
        d.text((x + (w - tw) / 2, cy), line, fill="#172033", font=title_font)
        cy += 38
    if sub_lines:
        cy += 6
    for line in sub_lines:
        tw, th = text_size(line, subtitle_font)
        d.text((x + (w - tw) / 2, cy), line, fill="#58677a", font=subtitle_font)
        cy += 30


def section(x: int, y: int, w: int, h: int, title: str):
    d.rounded_rectangle((x, y, x + w, y + h), radius=34, fill="#ffffff", outline="#e4eaf2", width=3)
    d.text((x + 34, y + 24), title, fill="#172033", font=SECTION)


def arrow(x1: int, y1: int, x2: int, y2: int, color: str = "#708199", width: int = 4):
    d.line((x1, y1, x2, y2), fill=color, width=width)
    dx = x2 - x1
    dy = y2 - y1
    if abs(dx) >= abs(dy):
        if dx >= 0:
            pts = [(x2, y2), (x2 - 18, y2 - 10), (x2 - 18, y2 + 10)]
        else:
            pts = [(x2, y2), (x2 + 18, y2 - 10), (x2 + 18, y2 + 10)]
    else:
        if dy >= 0:
            pts = [(x2, y2), (x2 - 10, y2 - 18), (x2 + 10, y2 - 18)]
        else:
            pts = [(x2, y2), (x2 - 10, y2 + 18), (x2 + 10, y2 + 18)]
    d.polygon(pts, fill=color)


def poly_arrow(points: list[tuple[int, int]], color: str = "#708199", width: int = 4):
    for a, b in zip(points, points[1:]):
        d.line((a[0], a[1], b[0], b[1]), fill=color, width=width)
    arrow(points[-2][0], points[-2][1], points[-1][0], points[-1][1], color, width)


def label(x: int, y: int, text: str, width: int):
    for line in wrap(text, width, TINY):
        d.text((x, y), line, fill="#58677a", font=TINY)
        y += 28


# Title
d.text((90, 70), "半句整体项目流程图：Web 主线 + Android 支线", fill="#172033", font=TITLE)
d.text(
    (94, 148),
    "Web 是当前可交付主线；Android 是原生迁移探索支线；两端共用服务端、内容资产与 AI 生成结果。",
    fill="#536275",
    font=SUB,
)

# Offline pipeline
section(70, 230, 2860, 440, "一、内容理解与生产链路")
pipeline_y = 360
pipeline = [
    (120, "短剧素材库", "视频 / 封面 / 字幕\n弹幕 CSV", "source"),
    (585, "内容导入与预处理", "扫描剧集，生成基础数据", "source"),
    (1050, "大模型离线理解", "高光、贴图、弹幕候选、二创分镜", "ai"),
    (1515, "人工复核工作台", "确认时间点、内容安全和展示质量", "review"),
    (1980, "FastAPI 服务端", "统一接口层 / 统计 / 复核", "server"),
]
for i, (x, title, sub, kind) in enumerate(pipeline):
    card(x, pipeline_y, 360, 140, title, sub, kind)
    if i:
        prev_x = pipeline[i - 1][0]
        arrow(prev_x + 360, pipeline_y + 70, x, pipeline_y + 70)

card(2400, 320, 230, 120, "SQLite", "后续迁移\nPostgreSQL", "server")
card(2660, 320, 230, 120, "资产缓存", "视频 / 贴图 / 图片\nmp3", "server")
card(2388, 500, 500, 120, "统一 API 层", "短剧 / 剧集 / 高光 / 弹幕\n互动 / 社交 / 声音 / 二创", "server")
poly_arrow([(2160, 500), (2515, 500), (2515, 440)], "#708199")
poly_arrow([(2160, 500), (2775, 500), (2775, 440)], "#708199")
arrow(2638, 440, 2638, 500, "#708199")

# Client branches
section(70, 720, 1380, 700, "二、Web 主线：当前比赛展示与完整体验")
section(1550, 720, 1380, 700, "三、Android 支线：原生迁移探索")

card(150, 830, 430, 130, "Web 主线客户端", "当前展示、答辩、复核和完整体验主版本", "web")
card(645, 830, 260, 118, "登录 / 选片首页", "高级视觉与选剧入口", "web")
card(965, 830, 260, 118, "Web 播放页", "沉浸观看主场景", "web")
card(150, 1050, 300, 130, "高光互动", "弹层 / 贴图 / 狂点", "web")
card(520, 1050, 300, 130, "弹幕三档", "轻聊 / 狂欢 / 沉浸", "web")
card(890, 1050, 300, 130, "片尾 AI 二创", "分支 / 图片 / 声音", "web")
card(1210, 1050, 170, 130, "同看社交", "房间 / 勋章 / 动态", "web", subtitle_font=TINY)
arrow(2388, 560, 1180, 790)
arrow(580, 895, 645, 895, "#f08c00")
arrow(905, 889, 965, 889, "#f08c00")
poly_arrow([(1095, 948), (1095, 1000), (300, 1000), (300, 1050)], "#f08c00")
arrow(450, 1115, 520, 1115, "#f08c00")
arrow(820, 1115, 890, 1115, "#f08c00")
arrow(1190, 1115, 1210, 1115, "#f08c00")

card(1630, 830, 430, 130, "Android 支线客户端", "验证原生播放、全屏、触摸\n权限 / App 化", "android")
card(2130, 830, 250, 118, "原生登录 / 选剧", "最小闭环", "android")
card(2440, 830, 250, 118, "原生播放页", "视频优先", "android")
card(2130, 1050, 250, 118, "高光时间轴", "按时间触发弹层", "android")
card(2440, 1050, 250, 118, "后续接入", "弹幕 / 二创 / 语音", "android")
arrow(2388, 560, 2005, 790)
arrow(2060, 895, 2130, 895, "#1683d8")
arrow(2380, 895, 2440, 895, "#1683d8")
poly_arrow([(2565, 948), (2565, 1000), (2255, 1000), (2255, 1050)], "#1683d8")
arrow(2380, 1109, 2440, 1109, "#1683d8")

label(130, 1288, "定位：Web 主线只考虑电脑端使用，不再承担手机套壳适配；重点保证观看页、高光互动、弹幕、片尾二创和答辩演示稳定。", 1120)
label(1610, 1288, "定位：Android 支线独立推进，不影响 Web 主线；先验证原生播放链路，再逐步迁移 Web 已验证的互动能力。", 1120)

# Cross-cutting systems
section(70, 1470, 2860, 430, "四、横向能力：弹幕治理、声音资产、数据回流")
card(130, 1585, 500, 125, "弹幕治理七层链路", "规则层 → 时间感知 → 语义分类 → 聚类去重 → 小模型 → 大模型复审候选 → 人工复核", "danmaku")
card(760, 1585, 360, 125, "用户授权声音样本", "我的页录音 / 上传", "voice")
card(1170, 1585, 300, 125, "Voice Profile", "保存授权和样本信息", "voice")
card(1520, 1585, 360, 125, "CosyVoice 生成 mp3", "预设文本生成并缓存", "voice")
card(2020, 1585, 360, 125, "用户互动数据回流", "点击 / 选择 / 弹幕\n二创 / 同看", "feedback")
card(2430, 1585, 360, 125, "统计与策略优化", "高光效果 / 弹幕治理\n二创内容", "feedback")
arrow(1120, 1648, 1170, 1648, "#d6336c")
arrow(1470, 1648, 1520, 1648, "#d6336c")
arrow(2380, 1648, 2430, 1648, "#ca8a04")

poly_arrow([(380, 1585), (380, 1380), (670, 1180)], "#16a34a")
poly_arrow([(1700, 1585), (1700, 1380), (1040, 1180)], "#d6336c")
poly_arrow([(2610, 1585), (2610, 1460), (1695, 1460), (1695, 500)], "#ca8a04")

label(130, 1765, "弹幕模块：不把弹幕当普通评论，而是结合剧情时间点、模式强度和内容安全进行治理。", 780)
label(760, 1765, "声音模块：服务端生成并缓存，不把 CosyVoice 模型放进客户端；用户声音只在授权后用于预设文本。", 780)
label(2020, 1765, "数据回流：用户互动结果进入统计和策略优化，反向帮助复核高光、弹幕和二创内容。", 760)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT, quality=95)
print(OUT.resolve())
