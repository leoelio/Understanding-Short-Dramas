import hashlib
import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.main import BEIWANG_EP1_REMIX_VARIANTS  # noqa: E402


OUT_DIR = ROOT / "frontend" / "assets" / "remix_videos" / "beiwang_ep1"
TEXT_DIR = ROOT / "data" / "render_text"
FONT = "C\\:/Windows/Fonts/msyh.ttc"
WIDTH = 480
HEIGHT = 854
FPS = 24


CHOICE_META = {
    "road_breakdown": {
        "label": "车坏在半路",
        "icon": "修",
        "accent": "0xff7a30",
        "soft": "0xffd36b",
        "tag": "返乡闯关",
    },
    "ticket_home": {
        "label": "借钱买票回家",
        "icon": "票",
        "accent": "0x4fc3ff",
        "soft": "0xffdf7e",
        "tag": "现实共情",
    },
    "kindness_ride": {
        "label": "帮人后一起回家",
        "icon": "善",
        "accent": "0x7ee787",
        "soft": "0xff9f43",
        "tag": "善意反转",
    },
}


def wrap_cjk(text: str, width: int, max_lines: int) -> str:
    clean = " ".join(str(text).replace("\n", " ").split())
    lines = [clean[index : index + width] for index in range(0, len(clean), width)]
    if len(lines) > max_lines:
        lines = lines[:max_lines]
        lines[-1] = lines[-1].rstrip("，。；：、 ") + "..."
    return "\n".join(lines)


def write_text(slot: str, name: str, value: str) -> str:
    path = TEXT_DIR / f"{slot}_{name}.txt"
    path.write_text(value, encoding="utf-8")
    return path.relative_to(ROOT).as_posix()


def between(start: float, end: float) -> str:
    return f"between(t\\,{start:.2f}\\,{end:.2f})"


def drawtext(
    textfile: str,
    fontsize: int,
    y: str,
    *,
    color: str = "white",
    x: str = "(w-text_w)/2",
    enable: str | None = None,
    box: bool = False,
) -> str:
    options = [
        f"fontfile='{FONT}'",
        f"textfile='{textfile}'",
        f"fontsize={fontsize}",
        f"fontcolor={color}",
        f"x={x}",
        f"y={y}",
        "line_spacing=8",
    ]
    if box:
        options.extend(["box=1", "boxcolor=0x05070a@0.54", "boxborderw=14"])
    if enable:
        options.append(f"enable='{enable}'")
    return "drawtext=" + ":".join(options)


def snow_layers() -> list[str]:
    layers = []
    for index in range(34):
        digest = hashlib.sha1(f"snow-{index}".encode("utf-8")).hexdigest()
        x = int(digest[:4], 16) % WIDTH
        offset = int(digest[4:8], 16) % HEIGHT
        speed = 34 + (int(digest[8:10], 16) % 72)
        size = 2 + (index % 3)
        alpha = 0.16 + (index % 4) * 0.04
        layers.append(
            f"drawbox=x={x}:y=mod(t*{speed}+{offset}\\,ih):w={size}:h={size * 3}:color=white@{alpha:.2f}:t=fill"
        )
    return layers


def render_one(choice_key: str, variant: dict) -> Path:
    meta = CHOICE_META[choice_key]
    slot = f"beiwang_ep1_{choice_key}_{variant['variant_key']}"
    output = OUT_DIR / f"{slot}.mp4"
    shots = variant["video_shots"]
    duration = sum(float(shot["duration_sec"]) for shot in shots)
    title_file = write_text(slot, "title", "北往 · 第1集 AI 二创")
    variant_file = write_text(slot, "variant", f"{meta['label']}｜{variant['label']}")
    summary_file = write_text(slot, "summary", wrap_cjk(variant["summary"], 18, 3))
    tag_file = write_text(slot, "tag", f"{meta['tag']} · {variant['runtime_sec']}秒以内")
    slot_file = write_text(slot, "slot", slot)

    filters = [
        "format=rgba",
        "noise=alls=8:allf=t+u",
        "drawbox=x=0:y=0:w=iw:h=ih:color=0x05070a@0.26:t=fill",
        "drawbox=x=0:y=ih*0.55:w=iw:h=ih*0.45:color=0x111820@0.78:t=fill",
        "drawbox=x=iw*0.12:y=ih*0.68:w=iw*0.76:h=ih*0.22:color=0x1f242c@0.88:t=fill",
        f"drawbox=x=0:y=0:w=8:h=ih:color={meta['accent']}@0.94:t=fill",
        f"drawbox=x=0:y=0:w=iw:h=4:color={meta['accent']}@0.80:t=fill",
        f"drawbox=x=0:y=ih-8:w=iw*t/{duration:.2f}:h=8:color={meta['soft']}@0.92:t=fill",
        "drawbox=x=iw*0.48:y=mod(t*120\\,ih):w=4:h=48:color=0xfff5d6@0.42:t=fill",
        "drawbox=x=iw*0.48:y=mod(t*120+120\\,ih):w=4:h=48:color=0xfff5d6@0.30:t=fill",
        *snow_layers(),
        drawtext(title_file, 22, "42", color="0xeef6ff"),
        drawtext(variant_file, 30, "82", color="white", box=True),
        drawtext(summary_file, 20, "142", color="0xd8e2ef"),
        drawtext(tag_file, 17, "h-96", color="0xfff0c2", x="34"),
        drawtext(slot_file, 11, "h-38", color="0x9ca8b8", x="34"),
    ]

    start = 0.0
    for index, shot in enumerate(shots, start=1):
        end = start + float(shot["duration_sec"])
        enable = between(start, end)
        shot_title = write_text(slot, f"shot_{index}_title", f"0{index}  {shot['caption']}")
        shot_subtitle = write_text(
            slot,
            f"shot_{index}_subtitle",
            wrap_cjk(variant["storyboard"][index - 1]["subtitle"], 17, 2),
        )
        shot_visual = write_text(
            slot,
            f"shot_{index}_visual",
            wrap_cjk(variant["storyboard"][index - 1]["visual"], 16, 3),
        )
        filters.extend(
            [
                f"drawbox=x=32:y=228:w=416:h=232:color=0x000000@0.20:t=fill:enable='{enable}'",
                f"drawbox=x=32:y=228:w=416:h=4:color={meta['accent']}@0.95:t=fill:enable='{enable}'",
                drawtext(shot_title, 25, "252", color="white", x="50", enable=enable),
                drawtext(shot_visual, 21, "306", color="0xdbe7f5", x="50", enable=enable),
                drawtext(shot_subtitle, 28, "h-246", color="white", enable=enable, box=True),
                drawtext(write_text(slot, f"sound_{index}", shot["sound"]), 15, "h-150", color="0xb7c4d8", enable=enable),
                drawtext(write_text(slot, f"icon_{index}", meta["icon"]), 76, "h-318", color="0xfff0c2", x="w-text_w-42", enable=enable),
            ]
        )
        start = end

    filter_complex = "[0:v]" + ",".join(filters) + ",format=yuv420p[v]"
    command = [
        "ffmpeg",
        "-y",
        "-f",
        "lavfi",
        "-i",
        f"color=c=0x071014:s={WIDTH}x{HEIGHT}:r={FPS}:d={duration:.2f}",
        "-f",
        "lavfi",
        "-i",
        "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-filter_complex",
        filter_complex,
        "-map",
        "[v]",
        "-map",
        "1:a",
        "-t",
        f"{duration:.2f}",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        "28",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "96k",
        "-movflags",
        "+faststart",
        str(output),
    ]
    subprocess.run(command, cwd=ROOT, check=True)
    return output


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if TEXT_DIR.exists():
        shutil.rmtree(TEXT_DIR)
    TEXT_DIR.mkdir(parents=True)
    outputs = []
    try:
        for choice_key, variants in BEIWANG_EP1_REMIX_VARIANTS.items():
            for variant in variants:
                outputs.append(render_one(choice_key, variant))
    finally:
        shutil.rmtree(TEXT_DIR, ignore_errors=True)
    for output in outputs:
        print(output.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()
