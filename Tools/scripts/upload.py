import contextlib
import html
import json
import mimetypes
import os
import re
import time
import uuid
from pathlib import Path
from sys import argv
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


BOT_TOKEN = os.environ.get("TG_BOT_TOKEN")
if not BOT_TOKEN and len(argv) > 1 and argv[1] and ":" in argv[1]:
    BOT_TOKEN = argv[1]

DEFAULT_CHAT_ID = os.environ.get("TG_UPLOAD_CHAT_ID")
if not DEFAULT_CHAT_ID and len(argv) > 2 and argv[2]:
    DEFAULT_CHAT_ID = argv[2]

PYROGRAM_SESSION_STRING = os.environ.get("TG_SESSION_STRING") or ""
PYROGRAM_API_ID = int(os.environ.get("APP_ID") or "0")
PYROGRAM_API_HASH = os.environ.get("APP_HASH") or ""
BOT_API_SIZE_LIMIT_BYTES = int(os.environ.get("TG_BOT_API_SIZE_LIMIT") or str(45 * 1024 * 1024))
MAX_FLOOD_WAIT_SECONDS = int(os.environ.get("TG_MAX_FLOOD_WAIT") or "1800")

artifacts_path = Path("artifacts")
test_version = any(arg.lower() == "test" for arg in argv[1:])
metadata_chat_id = argv[4] if len(argv) > 4 and argv[4] and argv[4] != DEFAULT_CHAT_ID else None


class TelegramUploadError(RuntimeError):
    def __init__(self, method: str, description: str):
        self.method = method
        self.description = description
        super().__init__(f"Telegram API {method} failed: {description}")


def find_apk(abi: str) -> Path | None:
    for artifact_dir in artifacts_path.glob("*"):
        if not artifact_dir.is_dir():
            continue
        for apk in artifact_dir.glob("*.apk"):
            if abi in apk.name.lower():
                return apk
    return None


def get_variant_info(file_path: Path | None) -> str:
    if not file_path:
        return ""
    name = file_path.name.lower()
    if "universal" in name:
        return "Universal"
    elif "arm64-v8a" in name:
        return "ARM64 (arm64-v8a)"
    elif "armeabi-v7a" in name:
        return "ARMv7 (armeabi-v7a)"
    elif "x86_64" in name:
        return "x86_64"
    elif "x86" in name:
        return "x86"
    return ""


def get_version_info(file_path: Path | None) -> str:
    if file_path:
        match = re.search(r"-v(\d+\.\d+(?:\.\d+)?(?:-[A-Za-z0-9.]+)*?)", file_path.name)
        if match:
            v = match.group(1).split("-")[0]
            if v:
                return v
    # Fallback to reading gradle.properties
    gradle_props = Path("gradle.properties")
    if gradle_props.exists():
        with contextlib.suppress(Exception):
            for line in gradle_props.read_text(encoding="utf-8").splitlines():
                if line.startswith("APP_VERSION_NAME="):
                    return line.split("=", 1)[1].strip()
    return ""


def get_commit_info() -> tuple[str, str, str]:
    commit_id_raw = os.environ.get("COMMIT_ID") or "unknown"
    commit_id = commit_id_raw[:7]
    commit_url = os.environ.get("COMMIT_URL")
    if not commit_url or not commit_url.startswith("http"):
        if commit_id and commit_id != "unknown":
            commit_url = f"https://github.com/alexandeer1/Alexgram/commit/{commit_id_raw}"
        else:
            commit_url = "https://github.com/alexandeer1/Alexgram/commits"
    commit_message = os.environ.get("COMMIT_MESSAGE") or "unknown"
    return commit_id, commit_url, commit_message


def normalize_message(text: str) -> str:
    return (text or "").replace("\\n", "\n")


def get_ai_summary() -> str:
    ai_summary = normalize_message(os.environ.get("AI_SUMMARY", "")).strip()
    commit_message = (os.environ.get("COMMIT_MESSAGE") or "").strip()
    if ai_summary and ai_summary != commit_message:
        return f"\n\n<blockquote expandable>{ai_summary}</blockquote>"
    return ""


def get_caption(file_path: Path | None = None) -> str:
    commit_id, commit_url, commit_message = get_commit_info()
    workflow = (os.environ.get("GITHUB_WORKFLOW") or "").lower()
    tier = (os.environ.get("APP_TIER") or "").lower()

    if tier == "nightly" or "nightly" in workflow:
        title = "Alexgram Nightly"
        icon = "🌙"
    elif tier == "beta" or "beta" in workflow:
        title = "Alexgram Beta"
        icon = "🧪"
    elif not test_version or "release" in workflow or tier == "release":
        title = "Alexgram Release"
        icon = "🚀"
    elif "canary" in workflow:
        title = "Alexgram Canary"
        icon = "🧪"
    elif "staging" in workflow:
        title = "Alexgram Staging"
        icon = "⚡"
    else:
        title = "Alexgram Test Build"
        icon = "🧪"

    variant = get_variant_info(file_path)
    version = get_version_info(file_path)

    version_badge = f" • <code>v{html.escape(version)}</code>" if version else ""
    variant_line = f"\n📦 <b>Variant:</b> <code>{html.escape(variant)}</code>" if variant else ""

    header = f"{icon} <b>{title}</b>{version_badge}{variant_line}\n\n📝 <b>Commit:</b>\n"
    footer = f"\n\n🔍 <a href=\"{html.escape(commit_url, quote=True)}\"><b>Commit Details:</b> <code>{html.escape(commit_id)}</code></a>"

    ai_summary = get_ai_summary()

    # Calculate available space within Telegram's 1024 char caption limit
    reserved_len = len(header) + len("<blockquote expandable></blockquote>") + len(footer) + len(ai_summary)
    max_msg_len = max(50, 1024 - reserved_len)

    msg_escaped = html.escape(commit_message.strip())
    if len(msg_escaped) > max_msg_len:
        msg_escaped = msg_escaped[: max_msg_len - 3].rstrip() + "..."

    body = f"<blockquote expandable>{msg_escaped}</blockquote>"
    full_caption = header + body + footer + ai_summary

    if len(full_caption) > 1024:
        full_caption = header + body + footer
        if len(full_caption) > 1024:
            available = max(20, 1024 - len(header) - len(footer) - 35)
            msg_escaped = msg_escaped[:available].rstrip() + "..."
            full_caption = header + f"<blockquote expandable>{msg_escaped}</blockquote>" + footer

    return full_caption


def get_metadata() -> str:
    commit_id, _, commit_message = get_commit_info()
    build_timestamp = os.environ.get("BUILD_TIMESTAMP") or "-1"

    commit_id_esc = html.escape(commit_id)
    commit_msg_esc = html.escape(commit_message)
    ts_esc = html.escape(build_timestamp)

    return (
        f"⏱ <b>Build:</b> <code>{ts_esc}</code> • <b>Commit:</b> <code>{commit_id_esc}</code>\n"
        f"📝 <code>{commit_msg_esc}</code>"
    )


def get_workflow_url() -> str:
    server_url = os.environ.get("GITHUB_SERVER_URL") or "https://github.com"
    repository = os.environ.get("GITHUB_REPOSITORY") or "alexandeer1/Alexgram"
    run_id = os.environ.get("GITHUB_RUN_ID")
    if run_id:
        return f"{server_url}/{repository}/actions/runs/{run_id}"
    return os.environ.get("COMMIT_URL") or f"{server_url}/{repository}/actions"


def get_large_file_notice(file_path: Path) -> str:
    caption = get_caption(file_path)
    workflow_url = get_workflow_url()
    notice = (
        "\n\n<blockquote expandable>"
        "APK was too large for direct Telegram Bot API upload from CI. "
        f"File: {html.escape(file_path.name)}. "
        f"Open workflow artifacts here: <a href=\"{html.escape(workflow_url, quote=True)}\">Workflow Runs</a>"
        "</blockquote>"
    )
    full_text = caption + notice
    if len(full_text) <= 4096:
        return full_text
    return notice[:4096]


def get_documents() -> list[dict[str, str | Path]]:
    apk_path_env = os.environ.get("APK_PATH")
    if apk_path_env:
        apk_path = Path(apk_path_env)
        if apk_path.is_dir():
            documents = []
            for apk in sorted(apk_path.rglob("*.apk")):
                documents.append({"path": apk, "caption": get_caption(apk)})
            return documents
        elif apk_path.is_file():
            return [{"path": apk_path, "caption": get_caption(apk_path)}]

    documents: list[dict[str, str | Path]] = []
    for abi in ["universal", "arm64-v8a", "armeabi-v7a", "x86_64", "x86"]:
        apk = find_apk(abi)
        if apk is not None:
            documents.append({"path": apk, "caption": get_caption(apk)})
    if not documents:
        for apk in sorted(artifacts_path.rglob("*.apk")):
            documents.append({"path": apk, "caption": get_caption(apk)})
    if not documents:
        documents.append({
            "path": Path("TMessagesProj/src/main/ic_launcher_nagram_block_round-playstore.png"),
            "caption": get_caption(),
        })
    return documents


def encode_multipart(fields: dict[str, str], files: dict[str, tuple[str, bytes, str]]) -> tuple[bytes, str]:
    boundary = "----AlexgramUpload" + uuid.uuid4().hex
    body = bytearray()

    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        body.extend(str(value).encode("utf-8"))
        body.extend(b"\r\n")

    for name, (filename, content, content_type) in files.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'.encode())
        body.extend(f"Content-Type: {content_type}\r\n\r\n".encode())
        body.extend(content)
        body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode())
    return bytes(body), f"multipart/form-data; boundary={boundary}"


def telegram_request(method: str, data: dict[str, str], files: dict[str, tuple[str, bytes, str]] | None = None) -> dict:
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/{method}"
    last_error: Exception | None = None

    for attempt in range(5):
        try:
            if files:
                body, content_type = encode_multipart(data, files)
                request = Request(url, data=body, method="POST")
                request.add_header("Content-Type", content_type)
            else:
                body = json.dumps(data).encode("utf-8")
                request = Request(url, data=body, method="POST")
                request.add_header("Content-Type", "application/json")

            with urlopen(request, timeout=300) as response:
                payload = json.loads(response.read().decode("utf-8"))
        except HTTPError as error:
            error_payload = error.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(error_payload)
            except json.JSONDecodeError:
                raise RuntimeError(f"Telegram API HTTP {error.code}: {error_payload}") from error
        except URLError as error:
            last_error = error
            time.sleep(min(2 ** attempt, 10))
            continue

        if payload.get("ok"):
            return payload

        retry_after = payload.get("parameters", {}).get("retry_after")
        if retry_after is not None:
            wait_seconds = int(retry_after) + 1
            print(f"Telegram rate limited {method}, retrying in {wait_seconds}s")
            time.sleep(wait_seconds)
            continue

        description = payload.get("description", "Unknown Telegram API error")
        raise TelegramUploadError(method, description)

    if last_error is not None:
        raise RuntimeError(f"Telegram API {method} failed after retries: {last_error}") from last_error
    raise RuntimeError(f"Telegram API {method} failed after retries")


def bot_api_get_chat(chat_id: str) -> dict | None:
    try:
        payload = telegram_request("getChat", {"chat_id": str(chat_id)})
    except Exception:
        return None
    return payload.get("result")


def coerce_chat_target(value: str | int) -> str | int:
    if isinstance(value, int):
        return value
    text = str(value).strip()
    if text.startswith("@"):
        return text
    with contextlib.suppress(ValueError):
        return int(text)
    return text


def can_use_pyrogram_fallback() -> bool:
    return bool(BOT_TOKEN and PYROGRAM_API_ID and PYROGRAM_API_HASH)


def send_document_via_pyrogram(chat_id: str, file_path: Path, caption: str = "") -> None:
    from pyrogram import Client
    from pyrogram.enums import ParseMode
    from pyrogram.errors import FloodWait
    from pyrogram.errors import PeerIdInvalid

    client_kwargs = {
        "name": "telegram_uploader",
        "api_id": PYROGRAM_API_ID,
        "api_hash": PYROGRAM_API_HASH,
        "in_memory": True,
    }
    if PYROGRAM_SESSION_STRING:
        client_kwargs["session_string"] = PYROGRAM_SESSION_STRING
    else:
        client_kwargs["bot_token"] = BOT_TOKEN

    def resolve_target(client: "Client", target: str | int) -> str | int:
        normalized_target = coerce_chat_target(target)
        try:
            client.get_chat(normalized_target)
            return normalized_target
        except Exception:
            pass

        # Populate peer cache from dialogs so numeric -100 IDs can be resolved.
        with contextlib.suppress(Exception):
            for dialog in client.get_dialogs(limit=500):
                if str(dialog.chat.id) == str(normalized_target):
                    return dialog.chat.id

        # Bridge via Bot API username when available.
        chat_info = bot_api_get_chat(str(normalized_target))
        if chat_info:
            username = chat_info.get("username")
            if username:
                username_target = f"@{username}"
                with contextlib.suppress(Exception):
                    client.get_chat(username_target)
                    return username_target

        return normalized_target

    with Client(**client_kwargs) as client:
        target = resolve_target(client, chat_id)
        for attempt in range(3):
            try:
                client.send_document(
                    chat_id=target,
                    document=str(file_path),
                    caption=caption,
                    parse_mode=ParseMode.HTML,
                    disable_notification=True,
                )
                return
            except PeerIdInvalid as error:
                # Retry one refresh cycle in case dialogs cache is stale.
                if attempt == 0:
                    target = resolve_target(client, chat_id)
                    continue
                raise RuntimeError(
                    "Pyrogram fallback could not resolve target peer. "
                    "If the chat is private without username, set TG_SESSION_STRING from an account that is in the group."
                ) from error
            except FloodWait as error:
                wait_seconds = int(error.value)
                if wait_seconds > MAX_FLOOD_WAIT_SECONDS:
                    raise RuntimeError(
                        f"FloodWait {wait_seconds}s exceeds configured limit {MAX_FLOOD_WAIT_SECONDS}s"
                    ) from error
                print(f"Pyrogram FloodWait while sending APK, sleeping {wait_seconds}s (attempt {attempt + 1}/3)")
                time.sleep(wait_seconds + 1)
        raise RuntimeError("Failed to send APK via Pyrogram after retries")


def should_use_pyrogram_for_file(file_path: Path) -> bool:
    return file_path.stat().st_size > BOT_API_SIZE_LIMIT_BYTES


def send_large_file_fallback(chat_id: str, file_path: Path, caption: str) -> None:
    if can_use_pyrogram_fallback():
        print(f"Using Pyrogram fallback for large file: {file_path.name}")
        send_document_via_pyrogram(chat_id, file_path, caption)
        return

    print(
        "Large file cannot be uploaded through Telegram Bot API and no Pyrogram session fallback is configured. "
        "Sending notice message instead."
    )
    send_message(chat_id, get_large_file_notice(file_path))


def send_document(chat_id: str, file_path: Path, caption: str = "") -> None:
    if should_use_pyrogram_for_file(file_path):
        send_large_file_fallback(chat_id, file_path, caption)
        return

    mime_type = mimetypes.guess_type(file_path.name)[0] or "application/octet-stream"
    fields = {
        "chat_id": str(chat_id),
        "parse_mode": "HTML",
        "disable_notification": "true",
    }
    if caption:
        fields["caption"] = caption
    files = {
        "document": (file_path.name, file_path.read_bytes(), mime_type),
    }
    try:
        telegram_request("sendDocument", fields, files)
    except TelegramUploadError as error:
        if "Request Entity Too Large" in error.description:
            send_large_file_fallback(chat_id, file_path, caption)
            return
        raise


def send_message(chat_id: str, text: str) -> None:
    telegram_request("sendMessage", {
        "chat_id": str(chat_id),
        "text": text,
        "parse_mode": "HTML",
        "disable_notification": "true",
    })


def send_to_channel(chat_id: str) -> None:
    documents = get_documents()
    for index, document in enumerate(documents):
        send_document(chat_id, document["path"], str(document["caption"] or ""))
        if index != len(documents) - 1:
            time.sleep(1)


def send_metadata(chat_id: str) -> None:
    send_message(chat_id, get_metadata())


def main() -> None:
    send_to_channel(DEFAULT_CHAT_ID)

    if metadata_chat_id and metadata_chat_id != DEFAULT_CHAT_ID:
        with contextlib.suppress(Exception):
            send_metadata(metadata_chat_id)


if __name__ == "__main__":
    main()
