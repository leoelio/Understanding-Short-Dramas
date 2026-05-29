import argparse
import base64
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from backend.app.main import BEIWANG_EP1_REMIX_VARIANTS  # noqa: E402


SOURCE_VIDEO = ROOT / "视频库" / "北往" / "第1集.mp4"
OUT_DIR = ROOT / "frontend" / "assets" / "remix_images" / "beiwang_ep1"

CHOICE_CLIP_STARTS = {
    "road_breakdown": [138, 214, 280],
    "ticket_home": [112, 145, 280],
    "kindness_ride": [240, 262, 280],
}


def slot_name(choice_key: str, variant: dict) -> str:
    return f"beiwang_ep1_{choice_key}_{variant['variant_key']}"


def output_path(choice_key: str, variant: dict, shot_index: int) -> Path:
    return OUT_DIR / f"{slot_name(choice_key, variant)}_shot_{shot_index}.png"


def build_prompt(choice_key: str, variant: dict, shot_index: int, shot: dict) -> str:
    storyboard = variant["storyboard"][shot_index - 1]
    return "\n".join(
        [
            "Vertical 9:16 cinematic still for a Chinese short drama extension.",
            "Use a realistic but slightly polished drama-poster style.",
            "No subtitles, no text, no watermark, no logo.",
            "Keep the mood grounded, emotional, and suitable for a mobile drama app.",
            f"Story direction: {variant['label']} / {variant['variable_label']}.",
            f"Shot: {shot.get('caption') or storyboard.get('shot')}.",
            f"Visual: {shot.get('video_prompt') or storyboard.get('visual')}.",
            f"Character emotion: {storyboard.get('subtitle', '')}",
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
    parser.add_argument("--mode", choices=["local-stills", "openai"], default="local-stills")
    parser.add_argument("--model", default="gpt-image-1")
    parser.add_argument("--quality", default="low", choices=["low", "medium", "high"])
    parser.add_argument("--size", default="1024x1536")
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()

    if args.mode == "local-stills" and not SOURCE_VIDEO.exists():
        raise FileNotFoundError(SOURCE_VIDEO)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    for choice_key, variants in BEIWANG_EP1_REMIX_VARIANTS.items():
        for variant in variants:
            for shot_index, shot in enumerate(variant["video_shots"], start=1):
                target = output_path(choice_key, variant, shot_index)
                if target.exists() and not args.overwrite:
                    print(f"skip {target.relative_to(ROOT).as_posix()}")
                    continue
                if args.mode == "openai":
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
