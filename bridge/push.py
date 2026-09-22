from __future__ import annotations

import hashlib
import json
import os
import re
import time
from typing import Any

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

from reliability import change_id

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
TRANSIENT_HTTP = {429, 500, 502, 503, 504}


def topic_for(code: str) -> str:
    normalized = re.sub(r"[^a-z0-9_.~%-]", "_", code.lower())
    return "kova_" + normalized


def _credentials():
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        return None, None

    info = json.loads(raw)
    project_id = info.get("project_id")
    if not project_id:
        raise RuntimeError("Firebase service account is missing project_id")

    credentials = service_account.Credentials.from_service_account_info(
        info,
        scopes=[FCM_SCOPE],
    )
    credentials.refresh(Request())
    return credentials, project_id


def configured() -> bool:
    return bool(os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip())


def _post_with_retry(endpoint: str, headers: dict, payload: dict, attempts: int = 4):
    last_response = None
    for attempt in range(attempts):
        response = requests.post(
            endpoint,
            timeout=20,
            headers=headers,
            json=payload,
        )
        last_response = response
        if response.status_code not in TRANSIENT_HTTP:
            response.raise_for_status()
            return response

        if attempt < attempts - 1:
            delay = 2 ** attempt
            print(f"FCM transient HTTP {response.status_code}; retrying in {delay}s")
            time.sleep(delay)

    assert last_response is not None
    last_response.raise_for_status()
    return last_response


def _send(
    credentials,
    project_id: str,
    org: dict,
    title: str,
    body: str,
    kind: str,
    source_url: str,
    event: dict | None = None,
    change_id_value: str | None = None,
) -> str:
    data = {
        "title": title,
        "body": body,
        "kind": kind,
        "organization": org["code"],
        "sourceUrl": source_url,
    }

    if change_id_value:
        data["changeId"] = change_id_value

    if event:
        data.update(
            {
                "eventId": str(event.get("id", "")),
                "dateIso": str(event.get("dateIso", "")),
                "dateLabel": str(event.get("dateLabel", "")),
                "time": str(event.get("time", "")),
                "eventType": str(event.get("type", "")),
                "description": str(event.get("description", "")),
                "sourceUrl": str(event.get("sourceUrl", source_url)),
            }
        )

    endpoint = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    payload: dict[str, Any] = {
        "message": {
            "topic": topic_for(org["code"]),
            "data": data,
            "android": {
                "priority": "high",
            },
        }
    }

    response = _post_with_retry(
        endpoint,
        headers={
            "Authorization": f"Bearer {credentials.token}",
            "Content-Type": "application/json; UTF-8",
        },
        payload=payload,
    )
    message_name = response.json().get("name", "ok")
    print(
        f"FCM sent to {topic_for(org['code'])}: "
        f"{message_name} ({kind}, changeId={change_id_value or '-'})"
    )
    return message_name


def send_diff_notification(org: dict, diff: dict, source_url: str) -> list[str]:
    added = diff["added"]
    removed = diff["removed"]
    changed = diff["changed"]
    total = len(added) + len(removed) + len(changed)

    if total == 0:
        return []

    credentials, project_id = _credentials()
    if credentials is None:
        print("FCM disabled: FIREBASE_SERVICE_ACCOUNT_JSON is not configured.")
        return []

    messages: list[dict] = []

    for event in added:
        messages.append(
            {
                "title": "Ny KOVA-aktivitet",
                "body": f"{event['description']} • {event['dateLabel']} {event['time']}",
                "kind": "added",
                "event": event,
                "changeId": change_id(org["code"], "added", event),
            }
        )

    for item in changed:
        old = item["old"]
        new = item["new"]
        messages.append(
            {
                "title": "KOVA-aktivitet endret",
                "body": (
                    f"{new['description']}: {old['dateLabel']} {old['time']} → "
                    f"{new['dateLabel']} {new['time']}"
                ),
                "kind": "changed",
                "event": new,
                "changeId": change_id(org["code"], "changed", new, old),
            }
        )

    for event in removed:
        messages.append(
            {
                "title": "KOVA-aktivitet fjernet",
                "body": f"{event['description']} • {event['dateLabel']} {event['time']}",
                "kind": "removed",
                "event": event,
                "changeId": change_id(org["code"], "removed", event),
            }
        )

    sent_ids: list[str] = []

    for message in messages[:10]:
        try:
            _send(
                credentials,
                project_id,
                org,
                message["title"],
                message["body"],
                message["kind"],
                source_url,
                message["event"],
                message["changeId"],
            )
            sent_ids.append(message["changeId"])
        except Exception as exc:
            print(
                f"FCM send failed for {message['changeId']}: {exc}",
                flush=True,
            )

    if len(messages) > 10:
        remaining = messages[10:]
        summary_raw = "|".join(item["changeId"] for item in remaining).encode("utf-8")
        summary_id = "summary-" + hashlib.sha256(summary_raw).hexdigest()[:24]
        try:
            _send(
                credentials,
                project_id,
                org,
                "Flere KOVA-endringer",
                f"{len(remaining)} ytterligere endringer er registrert. Åpne KOVA Companion for oversikt.",
                "summary",
                source_url,
                None,
                summary_id,
            )
            sent_ids.extend(item["changeId"] for item in remaining)
        except Exception as exc:
            print(f"FCM summary send failed: {exc}", flush=True)

    return sent_ids
