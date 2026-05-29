import argparse
import base64
import json
import os
import subprocess
import sys
import uuid
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.main import BEIWANG_EP1_REMIX_VARIANTS  # noqa: E402


SOURCE_VIDEO = ROOT / "视频库" / "北往" / "第1集.mp4"
OUT_DIR = ROOT / "frontend" / "assets" / "remix_images" / "beiwang_ep1"
REFERENCE_DIR = ROOT / "data" / "remix_image_refs" / "beiwang_ep1"

CHOICE_CLIP_STARTS = {
    "road_breakdown": [138, 214, 280],
    "ticket_home": [112, 145, 280],
    "kindness_ride": [240, 262, 280],
}

LEAD_REFERENCES = {
    "older_male_lead": 42,
    "younger_male_lead": 178,
}


def slot_name(choice_key: str, variant: dict) -> str:
    return f"beiwang_ep1_{choice_key}_{variant['variant_key']}"


def output_path(choice_key: str, variant: dict, shot_index: int) -> Path:
    return OUT_DIR / f"{slot_name(choice_key, variant)}_shot_{shot_index}.png"


def build_prompt(choice_key: str, variant: dict, shot_index: int, shot: dict) -> str:
    storyboard = variant["storyboard"][shot_index - 1]
    return "\n".join(
        [
            "Edit the uploaded episode stills into one vertical 9:16 cinematic still for a Chinese short drama extension.",
            "Use the uploaded stills as character and scene references, not as exact screenshots.",
            "The image must feature two male leads as the primary characters: one older, serious man from the reference still, and one younger worker from the reference still.",
            "Keep both male leads visually consistent with the episode references, with natural Chinese short-drama realism.",
            "Leave a clean lower-third safe area for app subtitle and voice-caption overlay.",
            "Do not draw any subtitles, text, logos, watermarks, road-sign words, UI, or captions inside the image.",
            "Use realistic lighting, grounded emotion, and mobile-drama composition.",
            f"Story direction: {variant['label']} / {variant['variable_label']}.",
            f"Shot: {shot.get('caption') or storyboard.get('shot')}.",
            f"Visual: {shot.get('video_prompt') or storyboard.get('visual')}.",
            f"Voice subtitle to reserve space for, but not draw: {storyboard.get('subtitle', '')}",
            f"Sound mood reference: {shot.get('sound') or storyboard.get('sound', '')}.",
        ]
    )


def load_openai_api_key() -> str | None:
    env_key = os.getenv("OPENAI_API_KEY")
    if env_key:
        return env_key
    env_path = ROOT / ".env"
    if not env_path.exists():
        return None
    for line in env_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        key, _, value = line.partition("=")
        if key.strip() == "OPENAI_API_KEY" and value.strip():
            return value.strip().strip('"').strip("'")
    return None


def render_source_still(choice_key: str, variant: dict, shot_index: int, target: Path) -> None:
    starts = CHOICE_CLIP_STARTS[choice_key]
    start = starts[min(shot_index - 1, len(starts) - 1)]
    command = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-ss",
        str(start),
        "-i",
        str(SOURCE_VIDEO),
        "-vf",
        "scale=1024:1536:force_original_aspect_ratio=increase,crop=1024:1536",
        "-frames:v",
        "1",
        str(target),
    ]
    subprocess.run(command, cwd=ROOT, check=True)


def extract_frame(second: int, target: Path) -> Path:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        return target
    command = [
        "ffmpeg",
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-ss",
        str(second),
        "-i",
        str(SOURCE_VIDEO),
        "-vf",
        "scale=1024:1536:force_original_aspect_ratio=increase,crop=1024:1536",
        "-frames:v",
        "1",
        str(target),
    ]
    subprocess.run(command, cwd=ROOT, check=True)
    return target


def reference_images(choice_key: str, shot_index: int) -> list[Path]:
    starts = CHOICE_CLIP_STARTS[choice_key]
    scene_second = starts[min(shot_index - 1, len(starts) - 1)]
    refs = [
        extract_frame(second, REFERENCE_DIR / f"{name}.png")
        for name, second in LEAD_REFERENCES.items()
    ]
    refs.append(extract_frame(scene_second, REFERENCE_DIR / f"{choice_key}_scene_{shot_index}.png"))
    return refs


def multipart_form_data(fields: dict[str, str], image_paths: list[Path]) -> tuple[bytes, str]:
    boundary = f"----codex-{uuid.uuid4().hex}"
    body = bytearray()
    for key, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{key}"\r\n\r\n'.encode("utf-8"))
        body.extend(str(value).encode("utf-8"))
        body.extend(b"\r\n")
    for image_path in image_paths:
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(
            (
                f'Content-Disposition: form-data; name="image[]"; filename="{image_path.name}"\r\n'
                "Content-Type: image/png\r\n\r\n"
            ).encode("utf-8")
        )
        body.extend(image_path.read_bytes())
        body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode("utf-8"))
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def openai_edit_image(prompt: str, target: Path, image_paths: list[Path], model: str, quality: str, size: str) -> None:
    api_key = load_openai_api_key()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY is required for --mode openai-edit")
    body, content_type = multipart_form_data(
        {
            "model": model,
            "prompt": prompt,
            "size": size,
            "quality": quality,
            "n": "1",
        },
        image_paths,
    )
    request = urllib.request.Request(
        "https://api.openai.com/v1/images/edits",
        data=body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": content_type,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=240) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI image edit failed: {error.code} {detail}") from error
    item = (data.get("data") or [{}])[0]
    if item.get("b64_json"):
        target.write_bytes(base64.b64decode(item["b64_json"]))
        return
    if item.get("url"):
        with urllib.request.urlopen(item["url"], timeout=180) as image_response:
            target.write_bytes(image_response.read())
        return
    raise RuntimeError("OpenAI response did not contain b64_json or url")


def openai_generate_image(prompt: str, target: Path, model: str, quality: str, size: str) -> None:
    api_key = load_openai_api_key()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY is required for --mode openai")
    payload = {
        "model": model,
        "prompt": prompt,
        "size": size,
        "quality": quality,
        "n": 1,
    }
    request = urllib.request.Request(
        "https://api.openai.com/v1/images/generations",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI image generation failed: {error.code} {detail}") from error
    item = (data.get("data") or [{}])[0]
    if item.get("b64_json"):
        target.write_bytes(base64.b64decode(item["b64_json"]))
        return
    if item.get("url"):
        with urllib.request.urlopen(item["url"], timeout=180) as image_response:
            target.write_bytes(image_response.read())
        return
    raise RuntimeError("OpenAI response did not contain b64_json or url")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate Beiwang episode 1 remix storyboard images.")
    parser.add_argument("--mode", choices=["local-stills", "openai", "openai-edit"], default="local-stills")
    parser.add_argument("--model", default="gpt-image-1")
    parser.add_argument("--quality", default="low", choices=["low", "medium", "high"])
    parser.add_argument("--size", default="1024x1536")
    parser.add_argument("--choice", choices=sorted(BEIWANG_EP1_REMIX_VARIANTS.keys()))
    parser.add_argument("--variant")
    parser.add_argument("--shot", type=int, choices=[1, 2, 3])
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    if args.mode in {"local-stills", "openai-edit"} and not SOURCE_VIDEO.exists():
        raise FileNotFoundError(SOURCE_VIDEO)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for choice_key, variants in BEIWANG_EP1_REMIX_VARIANTS.items():
        if args.choice and choice_key != args.choice:
            continue
        for variant in variants:
            if args.variant and variant["variant_key"] != args.variant:
                continue
            for shot_index, shot in enumerate(variant["video_shots"], start=1):
                if args.shot and shot_index != args.shot:
                    continue
                target = output_path(choice_key, variant, shot_index)
                if target.exists() and not args.overwrite:
                    print(f"skip {target.relative_to(ROOT).as_posix()}")
                    continue
                if args.mode == "openai-edit":
                    openai_edit_image(
                        build_prompt(choice_key, variant, shot_index, shot),
                        target,
                        reference_images(choice_key, shot_index),
                        args.model,
                        args.quality,
                        args.size,
                    )
                elif args.mode == "openai":
                    openai_generate_image(
                        build_prompt(choice_key, variant, shot_index, shot),
                        target,
                        args.model,
                        args.quality,
                        args.size,
                    )
                else:
                    render_source_still(choice_key, variant, shot_index, target)
                print(target.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()
