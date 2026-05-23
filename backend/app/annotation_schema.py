from typing import Any

from .taxonomy import (
    ALLOWED_EMOTIONS,
    HIGHLIGHT_TYPE_ALIASES,
    HIGHLIGHT_TYPE_LABELS,
    MAX_HIGHLIGHTS_PER_EPISODE,
    MIN_HIGHLIGHT_GAP_SEC,
    normalize_highlight_type,
)


ALLOWED_HIGHLIGHT_TYPES = HIGHLIGHT_TYPE_LABELS


def validate_annotation_payload(payload: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if not isinstance(payload, dict):
        return ["标注结果必须是 JSON 对象"]

    highlights = payload.get("highlights")
    if not isinstance(highlights, list):
        return ["字段 highlights 必须是数组"]
    if len(highlights) > MAX_HIGHLIGHTS_PER_EPISODE:
        errors.append(f"单集高光点建议不超过 {MAX_HIGHLIGHTS_PER_EPISODE} 个，避免互动过密")

    normalized_times: list[float] = []
    for index, item in enumerate(highlights):
        prefix = f"highlights[{index}]"
        if not isinstance(item, dict):
            errors.append(f"{prefix} 必须是对象")
            continue

        start = item.get("start_time_sec")
        end = item.get("end_time_sec")
        if not isinstance(start, (int, float)):
            errors.append(f"{prefix}.start_time_sec 必须是数字")
        if not isinstance(end, (int, float)):
            errors.append(f"{prefix}.end_time_sec 必须是数字")
        if isinstance(start, (int, float)) and isinstance(end, (int, float)) and end <= start:
            errors.append(f"{prefix}.end_time_sec 必须大于 start_time_sec")
        if isinstance(start, (int, float)):
            normalized_times.append(float(start))

        highlight_type = item.get("highlight_type")
        if isinstance(highlight_type, str) and highlight_type in HIGHLIGHT_TYPE_ALIASES:
            item["highlight_type"] = normalize_highlight_type(highlight_type)
        elif highlight_type not in ALLOWED_HIGHLIGHT_TYPES:
            errors.append(f"{prefix}.highlight_type 不在允许范围内")
        if item.get("emotion") not in ALLOWED_EMOTIONS:
            errors.append(f"{prefix}.emotion 不在允许范围内")
        if not item.get("title"):
            errors.append(f"{prefix}.title 不能为空")
        evidence_ids = item.get("evidence_segment_ids")
        if not isinstance(evidence_ids, list) or not evidence_ids:
            errors.append(f"{prefix}.evidence_segment_ids 必须是非空数组")
        evidence_text = item.get("evidence_text")
        if not isinstance(evidence_text, str) or not evidence_text.strip():
            errors.append(f"{prefix}.evidence_text 不能为空")

        options = item.get("options")
        if not isinstance(options, list) or not 2 <= len(options) <= 4:
            errors.append(f"{prefix}.options 必须是 2-4 个互动选项")
        else:
            seen = set()
            for option_index, option in enumerate(options):
                option_prefix = f"{prefix}.options[{option_index}]"
                if not isinstance(option, dict):
                    errors.append(f"{option_prefix} 必须是对象")
                    continue
                key = option.get("key")
                label = option.get("label")
                if not isinstance(key, str) or not key:
                    errors.append(f"{option_prefix}.key 不能为空")
                if key in seen:
                    errors.append(f"{option_prefix}.key 不能重复")
                seen.add(key)
                if not isinstance(label, str) or not label or len(label) > 8:
                    errors.append(f"{option_prefix}.label 必须是 1-8 个字")

    sorted_times = sorted(normalized_times)
    for previous, current in zip(sorted_times, sorted_times[1:]):
        if current - previous < MIN_HIGHLIGHT_GAP_SEC:
            errors.append(f"高光触发过密：{previous:.0f}s 与 {current:.0f}s 间隔不足 {MIN_HIGHLIGHT_GAP_SEC}s")

    return errors
