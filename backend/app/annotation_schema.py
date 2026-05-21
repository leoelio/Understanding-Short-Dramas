from typing import Any


ALLOWED_HIGHLIGHT_TYPES = {"冲突", "反转", "爽点", "甜蜜", "虐点", "搞笑", "悬念", "名场面"}
ALLOWED_EMOTIONS = {"爽", "震惊", "心疼", "愤怒", "好笑", "心动", "紧张", "期待"}


def validate_annotation_payload(payload: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    if not isinstance(payload, dict):
        return ["标注结果必须是 JSON 对象"]

    highlights = payload.get("highlights")
    if not isinstance(highlights, list):
        return ["字段 highlights 必须是数组"]

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

        if item.get("highlight_type") not in ALLOWED_HIGHLIGHT_TYPES:
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

    return errors
