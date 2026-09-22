from __future__ import annotations

import json
import os
import re
from typing import Any

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"


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


def _send(
    credentials,
    project_id: str,
    org: dict,
    title: str,
    body: str,
    kind: str,
    source_url: str,
    event: dict | None = None,
) -> None:
    data = {
        "title": title,
        "body": body,
        "kind": kind,
        "organization": org["code"],
        "sourceUrl": source_url,
    }

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

    response = requests.post(
        endpoint,
        timeout=20,
        headers={
            "Authorization": f"Bearer {credentials.token}",
            "Content-Type": "application/json; UTF-8",
        },
        json=payload,
    )
    response.raise_for_status()
    print(
        f"FCM sent to {topic_for(org['code'])}: "
        f"{response.json().get('name', 'ok')} ({kind})"
    )


def send_diff_notification(org: dict, diff: dict, source_url: str) -> bool:
    added = diff["added"]
    removed = diff["removed"]
    changed = diff["changed"]
    total = len(added) + len(removed) + len(changed)

    if total == 0:
        return False

    credentials, project_id = _credentials()
    if credentials is None:
        print("FCM disabled: FIREBASE_SERVICE_ACCOUNT_JSON is not configured.")
        return False

    messages: list[tuple[str, str, str, dict]] = []

    for event in added:
        messages.append(
            (
                "Ny KOVA-aktivitet",
                f"{event['description']} • {event['dateLabel']} {event['time']}",
                "added",
                event,
            )
        )

    for item in changed:
        old = item["old"]
        new = item["new"]
        messages.append(
            (
                "KOVA-aktivitet endret",
                f"{new['description']}: {old['dateLabel']} {old['time']} → "
                f"{new['dateLabel']} {new['time']}",
                "changed",
                new,
            )
        )

    for event in removed:
        messages.append(
            (
                "KOVA-aktivitet fjernet",
                f"{event['description']} • {event['dateLabel']} {event['time']}",
                "removed",
                event,
            )
        )

    # Normal KOVA changes are usually small. Cap the burst if a large import/change happens.
    for title, body, kind, event in messages[:10]:
        _send(
            credentials,
            project_id,
            org,
            title,
            body,
            kind,
            source_url,
            event,
        )

    if len(messages) > 10:
        remaining = len(messages) - 10
        _send(
            credentials,
            project_id,
            org,
            "Flere KOVA-endringer",
            f"{remaining} ytterligere endringer er registrert. Åpne KOVA Companion for oversikt.",
            "summary",
            source_url,
            None,
        )

    return True
