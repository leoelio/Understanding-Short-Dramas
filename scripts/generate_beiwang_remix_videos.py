import shutil
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.main import BEIWANG_EP1_REMIX_VARIANTS  # noqa: E402


SOURCE_VIDEO = ROOT / "视频库" / "北往" / "第1集.mp4"
OUT_DIR = ROOT / "frontend" / "assets" / "remix_videos" / "beiwang_ep1"
WORK_DIR = ROOT / "data" / "remix_render_work"
FONT = "C\\:/Windows/Fonts/msyh.ttc"


CHOICE_META = {
    "road_breakdown": {
        "label": "车坏在半路",
        "tag": "返乡闯关",
        "accent": "0xff7a30",
        "clip_starts": [138, 214, 280],
    },
    "ticket_home": {
        "label": "借钱买票回家",
        "tag": "现实共情",
        "accent": "0x4fc3ff",
        "clip_starts": [112, 145, 280],
    },
    "kindness_ride": {
        "label": "帮人后一起回家",
        "tag": "善意反转",
        "accent": "0x7ee787",
        "clip_starts": [240, 262, 280],
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
    path = WORK_DIR / f"{slot}_{name}.txt"
    path.write_text(value, encoding="utf-8")
    return path.as_posix().replace(":", "\\:")


def drawtext(
    textfile: str,
    fontsize: int,
    x: str,
    y: str,
    *,
    color: str = "white",
    box: bool = False,
) -> str:
    parts = [
        f"fontfile='{FONT}'",
        f"textfile='{textfile}'",
        f"fontsize={fontsize}",
        f"fontcolor={color}",
        f"x={x}",
        f"y={y}",
        "line_spacing=8",
    ]
    if box:
        parts.extend(["box=1", "boxcolor=0x05070a@0.62", "boxborderw=18"])
    return "drawtext=" + ":".join(parts)


def make_shot_video(choice_key: str, variant: dict, shot_index: int, start: float, shot: dict) -> Path:
    meta = CHOICE_META[choice_key]
    slot = f"beiwang_ep1_{choice_key}_{variant['variant_key']}"
    duration = float(shot["duration_sec"])
    output = WORK_DIR / f"{slot}_shot_{shot_index}.mp4"
    headline = write_text(slot, f"headline_{shot_index}", f"AI 预测 · {meta['label']}")
    variant_name = write_text(slot, f"variant_{shot_index}", f"{variant['label']} / {meta['tag']}")
    caption = write_text(slot, f"caption_{shot_index}", f"0{shot_index}  {shot['caption']}")
    subtitle = write_text(slot, f"subtitle_{shot_index}", wrap_cjk(variant["storyboard"][shot_index - 1]["subtitle"], 14, 2))
    visual = write_text(slot, f"visual_{shot_index}", wrap_cjk(variant["storyboard"][shot_index - 1]["visual"], 18, 2))
    footer = write_text(slot, f"footer_{shot_index}", "AI 猜测剧情，非正片内容")

    vf = ",".join(
        [
            "scale=720:1280:force_original_aspect_ratio=increase",
            "crop=720:1280",
            "eq=contrast=1.08:saturation=0.92:brightness=-0.035",
            "unsharp=5:5:0.55:3:3:0.2",
            "drawbox=x=0:y=0:w=iw:h=218:color=0x05070a@0.56:t=fill",
            "drawbox=x=0:y=ih-318:w=iw:h=318:color=0x05070a@0.64:t=fill",
            "drawbox=x=0:y=732:w=iw:h=238:color=0x05070a@0.98:t=fill",
            f"drawbox=x=0:y=0:w=8:h=ih:color={meta['accent']}@0.95:t=fill",
            f"drawbox=x=42:y=190:w=636:h=4:color={meta['accent']}@0.92:t=fill",
            f"drawbox=x=42:y=934:w=636:h=4:color={meta['accent']}@0.92:t=fill",
            drawtext(headline, 30, "42", "54", color="0xeef6ff"),
            drawtext(variant_name, 44, "42", "104", color="white"),
            drawtext(caption, 34, "42", "782", color="white"),
            drawtext(visual, 25, "42", "842", color="0xd7e2ef"),
            drawtext(subtitle, 40, "(w-text_w)/2", "1012", color="white", box=True),
            drawtext(footer, 18, "42", "h-56", color="0xc4cfde"),
        ]
    )
    command = [
        "ffmpeg",
        "-y",
        "-ss",
        str(start),
        "-t",
        f"{duration:.2f}",
        "-i",
        str(SOURCE_VIDEO),
        "-f",
        "lavfi",
        "-t",
        f"{duration:.2f}",
        "-i",
        "anullsrc=channel_layout=stereo:sample_rate=44100",
        "-map",
        "0:v:0",
        "-map",
        "1:a:0",
        "-vf",
        vf,
        "-r",
        "30",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        "20",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "96k",
        "-shortest",
        str(output),
    ]
    subprocess.run(command, cwd=ROOT, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return output


def render_one(choice_key: str, variant: dict) -> Path:
    slot = f"beiwang_ep1_{choice_key}_{variant['variant_key']}"
    output = OUT_DIR / f"{slot}.mp4"
    starts = CHOICE_META[choice_key]["clip_starts"]
    shot_files = [
        make_shot_video(choice_key, variant, index, starts[index - 1], shot)
        for index, shot in enumerate(variant["video_shots"], start=1)
    ]
    concat_file = WORK_DIR / f"{slot}_concat.txt"
    concat_file.write_text(
        "".join(f"file '{path.as_posix()}'\n" for path in shot_files),
        encoding="utf-8",
    )
    command = [
        "ffmpeg",
        "-y",
        "-f",
        "concat",
        "-safe",
        "0",
        "-i",
        str(concat_file),
        "-c",
        "copy",
        "-movflags",
        "+faststart",
        str(output),
    ]
    subprocess.run(command, cwd=ROOT, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return output


def main() -> None:
    if not SOURCE_VIDEO.exists():
        raise FileNotFoundError(SOURCE_VIDEO)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    shutil.rmtree(WORK_DIR, ignore_errors=True)
    WORK_DIR.mkdir(parents=True)
    try:
        outputs = [
            render_one(choice_key, variant)
            for choice_key, variants in BEIWANG_EP1_REMIX_VARIANTS.items()
            for variant in variants
        ]
    finally:
        shutil.rmtree(WORK_DIR, ignore_errors=True)
    for output in outputs:
        print(output.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()
