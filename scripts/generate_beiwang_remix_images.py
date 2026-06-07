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
    "pi_desheng_blue_overshirt": 145,
    "pi_desheng_scene_body": 265,
    "li_shixin_plaid_overshirt": 250,
    "li_shixin_scene_body": 95,
}

OPTIONAL_CORE_REFERENCES = [
    "motorcycle_departure_reference.png",
]


def slot_name(choice_key: str, variant: dict) -> str:
    return f"beiwang_ep1_{choice_key}_{variant['variant_key']}"


def output_path(choice_key: str, variant: dict, shot_index: int) -> Path:
    return OUT_DIR / f"{slot_name(choice_key, variant)}_shot_{shot_index}.png"


def image_safe_text(value: str | None) -> str:
    text = value or ""
    replacements = {
        "被偷": "临时不见",
        "偷": "不见",
        "陷进雪沟": "停在雪边",
        "陷在雪沟": "停在雪边",
        "医院": "镇上办事地点",
        "冻得发抖": "冷得缩着肩",
    }
    for source, target in replacements.items():
        text = text.replace(source, target)
    return text


def story_scene_guidance(choice_key: str, variant: dict) -> str:
    variant_key = variant["variant_key"]
    if choice_key == "road_breakdown":
        return "Use a medium or wide roadside story composition, not a close-up portrait. The old motorcycle and the specific breakdown detail must be clearly visible."
    if choice_key == "ticket_home":
        return "Use a medium or wide transport story composition, not a close-up portrait. The station, train, coach bus, ticket, or crowded carriage must be clearly visible; any paper text must be blank or unreadable."
    if variant_key == "wuling_van":
        return "Use a medium or wide roadside story composition. A white Wuling Hongguang-style minivan must be clearly visible, with a small red Wuling-like grille badge shape or badge position visible, but no readable license plate or readable text."
    if variant_key == "audi_sedan":
        return "Use a medium or wide roadside story composition. A dark Audi-style luxury sedan must be clearly visible, with a subtle four-ring-like grille badge position visible, but no readable license plate or readable text."
    if variant_key == "range_rover_suv":
        return "Use a medium or wide snowy roadside rescue composition. A dark Range Rover-style luxury SUV must be clearly visible, with a boxy premium silhouette, rectangular grille, and a non-readable hood badge area, but no readable license plate or readable lettering."
    return ""


def build_prompt(choice_key: str, variant: dict, shot_index: int, shot: dict) -> str:
    storyboard = variant["storyboard"][shot_index - 1]
    timeline_rule = ""
    if choice_key == "road_breakdown" and shot_index == 1:
        timeline_rule = "This shot happens before the drink purchase. Do not show any drink can, bottle, beverage, soda, water bottle, or cup in this image."
    if choice_key != "road_breakdown":
        timeline_rule = "This branch is not a drink branch. Do not show any drink can, bottle, beverage, soda, water bottle, or cup in this image."
    if choice_key == "ticket_home" and variant["variant_key"] == "green_train" and shot_index == 2:
        timeline_rule += " The father and little girl are separate passerby passengers in the waiting area. Do not make either male lead the child's father, and do not place the child between the two leads as their companion; the two male leads should observe nearby and be emotionally moved."
    if choice_key == "kindness_ride" and shot_index in {2, 3}:
        timeline_rule += " The old motorcycles are already missing in this part of the story. Do not show the old motorcycle itself, its handlebar, wheel, mirror, seat, headlight, or any parked motorcycle anywhere in the foreground or background. Show only an empty snowy spot, drag marks, loose ropes, luggage, and the helped vehicle nearby."
    return "\n".join(
        [
            "Edit the uploaded episode stills into one vertical 9:16 cinematic still for a Chinese short drama extension.",
            "Create one single continuous cinematic scene, not a collage, not a split-screen, not a comic panel, not a storyboard sheet, and not multiple stacked frames.",
            "Use the uploaded stills as character and scene references, not as exact screenshots.",
            "Uploaded reference stills may contain subtitles, storefront signs, road signs, or frame text; ignore all text in the references and do not reproduce it.",
            "Story continuity begins from the episode ending: both male leads riding their own old motorcycles on an open road. If a two-rider motorcycle departure still is uploaded, treat it as the highest-priority reference for the two leads, the two motorcycles, luggage positions, road depth, and departure mood. Only show the motorcycles in shots where the story logically still includes them.",
            "The image must feature the two correct male leads as the primary characters: Pi Desheng, a young man with messy black hair, blue-gray overshirt and red inner shirt; and Li Shixin, a young man with short black hair, clearly visible beige plaid/checkered overshirt and gray tee.",
            "Keep both male leads visually consistent with the episode references, with natural Chinese short-drama realism.",
            "Do not replace either lead with the early debt-collector character in a burgundy striped polo.",
            "Leave a clean lower-third safe area for app subtitle and voice-caption overlay.",
            "Do not draw any subtitles, readable text, watermarks, road-sign words, UI, or captions inside the image.",
            "Any papers, tickets, phone screens, signs, labels, plates, and route boards must be blank or unreadable.",
            "All drink cans and bottles must be completely plain solid-color props: no readable words, no brand marks, no fake logos, no decorative graphics, no barcode, no nutrition panel, no illustrations, and no patterned label. If the drink surface is visible, it must be a clean blank surface.",
            "Only show the selected drink in shots where the shot caption or visual explicitly mentions buying, drinking, holding, placing, or carrying that drink; do not foreshadow the drink in earlier breakdown shots.",
            timeline_rule,
            "For vehicle-brand variants, show the selected vehicle through silhouette, grille shape, badge position, and brand-like emblem geometry; do not rely on readable brand words or readable license plates.",
            "Use realistic lighting, grounded emotion, and mobile-drama composition.",
            story_scene_guidance(choice_key, variant),
            f"Story direction: {image_safe_text(variant['label'])} / {image_safe_text(variant['variable_label'])}.",
            f"Shot: {image_safe_text(shot.get('caption') or storyboard.get('shot'))}.",
            f"Visual: {image_safe_text(shot.get('video_prompt') or storyboard.get('visual'))}.",
            "Reserve empty lower-third space for app-rendered dialogue later, but do not draw any dialogue text in the image.",
            f"Sound mood reference: {image_safe_text(shot.get('sound') or storyboard.get('sound', ''))}.",
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
        REFERENCE_DIR / name
        for name in OPTIONAL_CORE_REFERENCES
        if (REFERENCE_DIR / name).exists()
    ]
    refs.extend(
        extract_frame(second, REFERENCE_DIR / f"{name}.png")
        for name, second in LEAD_REFERENCES.items()
    )
    scene_ref = REFERENCE_DIR / f"{choice_key}_scene_{shot_index}.png"
    if scene_ref.exists():
        refs.append(scene_ref)
    else:
        refs.append(extract_frame(scene_second, scene_ref))
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
