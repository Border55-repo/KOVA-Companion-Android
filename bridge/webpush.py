from __future__ import annotations

import base64
import hashlib
import json
import os
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

import requests
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from google.auth.transport.requests import Request
from google.oauth2 import service_account
from pywebpush import WebPushException, webpush

FIRESTORE_SCOPE = "https://www.googleapis.com/auth/datastore"
PUBLIC_CONFIG_PATH = Path(__file__).resolve().parent / "data" / "webpush-config.json"
VAPID_DOC = "webPushConfig/vapid"
SUBSCRIPTIONS_COLLECTION = "webPushSubscriptions"
REMINDERS_COLLECTION = "webPushReminders"
REMINDER_STATE_COLLECTION = "webPushReminderState"
OSLO = ZoneInfo("Europe/Oslo")


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _credentials():
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        return None, None
    info = json.loads(raw)
    credentials = service_account.Credentials.from_service_account_info(
        info,
        scopes=[FIRESTORE_SCOPE],
    )
    credentials.refresh(Request())
    return credentials, info["project_id"]


def _headers(credentials) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {credentials.token}",
        "Content-Type": "application/json",
    }


def _doc_url(project_id: str, path: str) -> str:
    return (
        "https://firestore.googleapis.com/v1/"
        f"projects/{project_id}/databases/(default)/documents/{path}"
    )


def _string_field(document: dict, key: str) -> str:
    return str(((document.get("fields") or {}).get(key) or {}).get("stringValue", ""))


def _bool_field(document: dict, key: str, default: bool = False) -> bool:
    value = ((document.get("fields") or {}).get(key) or {}).get("booleanValue")
    return default if value is None else bool(value)


def _array_strings(document: dict, key: str) -> list[str]:
    values = (((document.get("fields") or {}).get(key) or {}).get("arrayValue") or {}).get("values", [])
    return [str(item.get("stringValue", "")) for item in values if item.get("stringValue")]


def _has_field(document: dict, key: str) -> bool:
    return key in (document.get("fields") or {})


def _integer_field(document: dict, key: str, default: int = 0) -> int:
    value = ((document.get("fields") or {}).get(key) or {}).get("integerValue")
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _document_id(document: dict) -> str:
    return str(document.get("name", "")).rsplit("/", 1)[-1]


def _list_collection_documents(
    credentials,
    project_id: str,
    collection: str,
) -> list[dict]:
    url = _doc_url(project_id, collection)
    documents: list[dict] = []
    token = None

    while True:
        params: dict[str, Any] = {"pageSize": 500}
        if token:
            params["pageToken"] = token
        response = requests.get(url, headers=_headers(credentials), params=params, timeout=30)
        if response.status_code == 404:
            return []
        response.raise_for_status()
        payload = response.json()
        documents.extend(payload.get("documents", []))
        token = payload.get("nextPageToken")
        if not token:
            return documents


def ensure_vapid_config() -> dict[str, str]:
    credentials, project_id = _credentials()
    if credentials is None:
        raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON is required for Web Push setup")

    url = _doc_url(project_id, VAPID_DOC)
    response = requests.get(url, headers=_headers(credentials), timeout=30)

    if response.status_code == 404:
        private_key = ec.generate_private_key(ec.SECP256R1())
        private_der = private_key.private_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        )
        public_raw = private_key.public_key().public_bytes(
            encoding=serialization.Encoding.X962,
            format=serialization.PublicFormat.UncompressedPoint,
        )
        public_key = _b64url(public_raw)
        private_key_encoded = _b64url(private_der)

        payload = {
            "fields": {
                "publicKey": {"stringValue": public_key},
                "privateKey": {"stringValue": private_key_encoded},
                "createdAt": {"timestampValue": datetime.now(timezone.utc).isoformat()},
            }
        }
        response = requests.patch(url, headers=_headers(credentials), json=payload, timeout=30)
        response.raise_for_status()
        document = response.json()
        print("Created persistent VAPID key pair in server-only Firestore config.")
    else:
        response.raise_for_status()
        document = response.json()
        public_key = _string_field(document, "publicKey")
        private_key_encoded = _string_field(document, "privateKey")
        if not public_key or not private_key_encoded:
            raise RuntimeError("Stored VAPID configuration is incomplete")
        print("Persistent VAPID key pair already exists.")

    public_payload = {
        "schemaVersion": 1,
        "publicKey": public_key,
    }
    PUBLIC_CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    PUBLIC_CONFIG_PATH.write_text(
        json.dumps(public_payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return {
        "publicKey": public_key,
        "privateKey": private_key_encoded,
        "projectId": project_id,
    }


def _list_subscription_documents(credentials, project_id: str) -> list[dict]:
    return _list_collection_documents(
        credentials,
        project_id,
        SUBSCRIPTIONS_COLLECTION,
    )


def _webpush_status(exc: WebPushException) -> int | None:
    direct = getattr(exc, "status_code", None)
    if direct is not None:
        return direct
    response = getattr(exc, "response", None)
    return getattr(response, "status_code", None)


def _disable_subscription(credentials, document_name: str) -> None:
    url = f"https://firestore.googleapis.com/v1/{document_name}"
    response = requests.patch(
        url,
        headers=_headers(credentials),
        params={"updateMask.fieldPaths": "enabled"},
        json={"fields": {"enabled": {"booleanValue": False}}},
        timeout=30,
    )
    if not response.ok:
        print(f"Could not disable stale Web Push subscription: {response.status_code}")


def _quiet_now(document: dict, now: datetime | None = None) -> bool:
    if not _bool_field(document, "quietHoursEnabled", False):
        return False
    start = _integer_field(document, "quietStartHour", 22)
    end = _integer_field(document, "quietEndHour", 7)
    hour = (now or datetime.now(OSLO)).astimezone(OSLO).hour
    if start == end:
        return True
    if start < end:
        return start <= hour < end
    return hour >= start or hour < end


def _subscription_allows(
    document: dict,
    kind: str,
    event: dict | None,
) -> bool:
    kinds = _array_strings(document, "notificationKinds")
    if _has_field(document, "notificationKinds") and kind not in kinds:
        return False
    disabled_types = set(_array_strings(document, "disabledEventTypes"))
    event_type = str((event or {}).get("type", "")).strip()
    if event_type and event_type in disabled_types:
        return False
    if kind != "reminder" and _quiet_now(document):
        return False
    return True


def send_web_notification(
    organization: str,
    title: str,
    body: str,
    kind: str,
    event: dict | None,
    change_id_value: str,
) -> bool:
    credentials, project_id = _credentials()
    if credentials is None:
        print("Web Push disabled: Firebase service account is not configured.")
        return True

    config = ensure_vapid_config()
    documents = _list_subscription_documents(credentials, project_id)
    targets = [
        document
        for document in documents
        if _bool_field(document, "enabled", True)
        and organization in _array_strings(document, "organizations")
        and _subscription_allows(document, kind, event)
    ]

    print(
        f"Web Push subscriptions: total={len(documents)}, "
        f"matching={len(targets)} for {organization}."
    )
    if not targets:
        return True

    payload = json.dumps(
        {
            "title": title,
            "body": body,
            "kind": kind,
            "organization": organization,
            "changeId": change_id_value,
            "changeSummary": body if kind == "changed" else "",
            "event": event or {},
        },
        ensure_ascii=False,
    )

    transient_failure = False
    sent = 0
    for document in targets:
        subscription = {
            "endpoint": _string_field(document, "endpoint"),
            "keys": {
                "p256dh": _string_field(document, "p256dh"),
                "auth": _string_field(document, "auth"),
            },
        }
        if not all([subscription["endpoint"], subscription["keys"]["p256dh"], subscription["keys"]["auth"]]):
            continue
        try:
            webpush(
                subscription_info=subscription,
                data=payload,
                vapid_private_key=config["privateKey"],
                vapid_claims={"sub": "mailto:kova-companion@users.noreply.github.com"},
                ttl=3600,
                timeout=20,
            )
            sent += 1
        except WebPushException as exc:
            status = _webpush_status(exc)
            if status in (404, 410):
                _disable_subscription(credentials, document["name"])
                print(f"Disabled stale Web Push subscription ({status}).")
            else:
                transient_failure = True
                print(f"Web Push failed ({status or 'unknown'}).", flush=True)

    print(f"Web Push sent to {sent}/{len(targets)} subscription(s) for {organization}.")
    return not transient_failure



def send_web_announcement(title: str, body: str, change_id_value: str, *,
                          audience="all", organization="", subscription_id="") -> dict[str, int]:
    """Deliver only to explicitly selected enabled subscriptions; fail closed."""
    if audience not in ("all", "organization", "test"):
        raise ValueError("Invalid audience")
    if audience == "test" and not re.fullmatch(r"[a-f0-9]{64}", subscription_id):
        raise ValueError("Invalid test device")
    if audience == "organization" and not organization:
        raise ValueError("Missing organization")
    credentials, project_id = _credentials()
    if credentials is None:
        raise RuntimeError("Firebase service account is not configured for Web Push")

    config = ensure_vapid_config()
    documents = _list_subscription_documents(credentials, project_id)
    targets = [
        document for document in documents
        if _bool_field(document, "enabled", True)
        and (audience != "test" or document.get("name", "").rsplit("/", 1)[-1] == subscription_id)
        and (audience != "organization" or organization in _array_strings(document, "organizations"))
    ]
    print(f"Global Web Push announcement subscriptions: total={len(documents)}, enabled={len(targets)}.")
    payload = {
        "title": title,
        "body": body,
        "kind": "announcement",
        "organization": "",
        "changeId": change_id_value,
        "changeSummary": body,
        "url": "https://border55-repo.github.io/KOVA-Companion-Android/#changelogCard",
        "event": {},
    }

    result = {"targets": len(targets), "accepted": 0, "failed": 0, "expired": 0}
    for document in targets:
        try:
            ok, delivered = _send_to_subscription(document, payload, config, credentials)
        except Exception:
            ok, delivered = False, False
        if not ok:
            result["failed"] += 1
        elif delivered:
            result["accepted"] += 1
        else:
            result["expired"] += 1

    print("Global Web Push announcement: " + json.dumps(result), flush=True)
    return result

def _semantic_key(event: dict) -> str:
    normalize = lambda value: re.sub(r"\s+", " ", str(value or "").strip().lower())
    return normalize(event.get("type")) + "|" + normalize(event.get("description"))


def _event_datetime(event: dict) -> datetime | None:
    date_iso = str(event.get("dateIso", "")).strip()
    time_match = re.search(r"\d{1,2}:\d{2}", str(event.get("time", "")))
    if not date_iso or not time_match:
        return None
    try:
        value = datetime.strptime(
            date_iso + " " + time_match.group(0),
            "%Y-%m-%d %H:%M",
        )
    except ValueError:
        return None
    return value.replace(tzinfo=OSLO)


def reminder_due(
    now: datetime,
    event: dict,
    lead_minutes: int,
    grace_minutes: int = 20,
) -> bool:
    event_at = _event_datetime(event)
    if event_at is None or lead_minutes <= 0:
        return False
    if now.tzinfo is None:
        now = now.replace(tzinfo=OSLO)
    else:
        now = now.astimezone(OSLO)
    due_at = event_at - timedelta(minutes=lead_minutes)
    return due_at <= now < event_at and now <= due_at + timedelta(minutes=grace_minutes)


def _reminder_signature(
    organization: str,
    event: dict,
    lead_minutes: int,
) -> str:
    raw = "|".join(
        [
            organization,
            str(event.get("id", "")),
            str(event.get("dateIso", "")),
            str(event.get("time", "")),
            str(lead_minutes),
        ]
    ).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def _get_reminder_state_signature(
    credentials,
    project_id: str,
    reminder_id: str,
) -> str:
    response = requests.get(
        _doc_url(project_id, f"{REMINDER_STATE_COLLECTION}/{reminder_id}"),
        headers=_headers(credentials),
        timeout=30,
    )
    if response.status_code == 404:
        return ""
    response.raise_for_status()
    return _string_field(response.json(), "signature")


def _write_reminder_state(
    credentials,
    project_id: str,
    reminder_id: str,
    signature: str,
    event: dict,
) -> None:
    payload = {
        "fields": {
            "signature": {"stringValue": signature},
            "eventId": {"stringValue": str(event.get("id", ""))},
            "sentAt": {"timestampValue": datetime.now(timezone.utc).isoformat()},
        }
    }
    response = requests.patch(
        _doc_url(project_id, f"{REMINDER_STATE_COLLECTION}/{reminder_id}"),
        headers=_headers(credentials),
        json=payload,
        timeout=30,
    )
    response.raise_for_status()


def _lead_label(minutes: int) -> str:
    if minutes % (24 * 60) == 0:
        days = minutes // (24 * 60)
        return f"{days} dag" if days == 1 else f"{days} dager"
    if minutes % 60 == 0:
        hours = minutes // 60
        return f"{hours} time" if hours == 1 else f"{hours} timer"
    return f"{minutes} minutter"


def _send_to_subscription(
    document: dict,
    payload: dict,
    config: dict[str, str],
    credentials,
) -> tuple[bool, bool]:
    subscription = {
        "endpoint": _string_field(document, "endpoint"),
        "keys": {
            "p256dh": _string_field(document, "p256dh"),
            "auth": _string_field(document, "auth"),
        },
    }
    if not all(
        [
            subscription["endpoint"],
            subscription["keys"]["p256dh"],
            subscription["keys"]["auth"],
        ]
    ):
        return False, False

    try:
        webpush(
            subscription_info=subscription,
            data=json.dumps(payload, ensure_ascii=False),
            vapid_private_key=config["privateKey"],
            vapid_claims={"sub": "mailto:kova-companion@users.noreply.github.com"},
            ttl=3600,
            timeout=20,
        )
        return True, True
    except WebPushException as exc:
        status = _webpush_status(exc)
        if status in (404, 410):
            _disable_subscription(credentials, document["name"])
            print(f"Disabled stale reminder subscription ({status}).")
            return True, False
        print(f"Web Push failed ({status or 'unknown'}).", flush=True)
        return False, False


def send_due_reminders(
    events_by_organization: dict[str, list[dict]],
    now: datetime | None = None,
) -> int:
    credentials, project_id = _credentials()
    if credentials is None:
        return 0

    reminders = _list_collection_documents(
        credentials,
        project_id,
        REMINDERS_COLLECTION,
    )
    if not reminders:
        return 0

    subscriptions = {
        _document_id(document): document
        for document in _list_subscription_documents(credentials, project_id)
    }
    config = ensure_vapid_config()
    now = (now or datetime.now(OSLO)).astimezone(OSLO)
    sent = 0

    for reminder in reminders:
        if not _bool_field(reminder, "enabled", True):
            continue

        reminder_id = _document_id(reminder)
        subscription_id = _string_field(reminder, "subscriptionId")
        organization = _string_field(reminder, "organization")
        event_id = _string_field(reminder, "eventId")
        semantic_key = _string_field(reminder, "semanticKey")
        lead_minutes = _integer_field(reminder, "leadMinutes")

        subscription = subscriptions.get(subscription_id)
        if subscription is None or not _bool_field(subscription, "enabled", True):
            continue
        if organization not in _array_strings(subscription, "organizations"):
            continue

        events = events_by_organization.get(organization, [])
        event = next(
            (item for item in events if str(item.get("id", "")) == event_id),
            None,
        )
        if event is None and semantic_key:
            candidates = [
                item for item in events
                if _semantic_key(item) == semantic_key
            ]
            if len(candidates) == 1:
                event = candidates[0]

        if event is None or not reminder_due(now, event, lead_minutes):
            continue

        signature = _reminder_signature(
            organization,
            event,
            lead_minutes,
        )
        if (
            _get_reminder_state_signature(
                credentials,
                project_id,
                reminder_id,
            )
            == signature
        ):
            continue

        label = _lead_label(lead_minutes)
        payload = {
            "title": "Påminnelse om KOVA-vakt",
            "body": (
                f"{event.get('description', 'KOVA-aktivitet')} starter om {label} • "
                f"{event.get('dateLabel', event.get('dateIso', ''))} "
                f"{event.get('time', '')}"
            ).strip(),
            "kind": "reminder",
            "organization": organization,
            "changeId": "reminder-" + reminder_id + "-" + signature[:12],
            "event": event,
        }

        ok, delivered = _send_to_subscription(
            subscription,
            payload,
            config,
            credentials,
        )
        if not ok:
            continue

        _write_reminder_state(
            credentials,
            project_id,
            reminder_id,
            signature,
            event,
        )
        if delivered:
            sent += 1

    if sent:
        print(f"Web Push reminders sent: {sent}.")
    return sent
