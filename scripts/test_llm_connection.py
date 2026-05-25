import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path

from dotenv import load_dotenv


ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / ".env")


def redact(text: str) -> str:
    text = re.sub(r"ark-[A-Za-z0-9-]+", "[redacted_api_key]", text)
    text = re.sub(r"ep-[A-Za-z0-9-]+", "[redacted_endpoint]", text)
    return text[:500]


def main() -> None:
    api_key = os.getenv("ARK_API_KEY")
    model = os.getenv("ARK_MODEL") or os.getenv("ARK_ENDPOINT_ID")
    base_url = os.getenv("ARK_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3").rstrip("/")
    result = {
        "configured": {
            "api_key": bool(api_key),
            "model": bool(model),
            "base_url": bool(base_url),
        },
        "ok": False,
    }
    if not api_key or not model:
        result["error"] = "missing_local_env"
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": "你是连通性测试助手。"},
            {"role": "user", "content": "只回复 OK。"},
        ],
        "temperature": 0,
        "max_tokens": 8,
    }
    request = urllib.request.Request(
        f"{base_url}/chat/completions",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.loads(response.read().decode("utf-8"))
        content = data["choices"][0]["message"]["content"]
        result["ok"] = True
        result["reply_length"] = len(content)
    except urllib.error.HTTPError as exc:
        result["status"] = exc.code
        result["error"] = "http_error"
        result["body"] = redact(exc.read().decode("utf-8", errors="replace"))
    except urllib.error.URLError as exc:
        result["error"] = "url_error"
        result["reason"] = redact(str(exc.reason))
    except Exception as exc:
        result["error"] = exc.__class__.__name__
        result["reason"] = redact(str(exc))

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
