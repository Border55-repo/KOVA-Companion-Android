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


def _message_for(org: dict, diff: dict) -> tuple[str, str, str]:
    added = diff["added"]
    removed = diff["removed"]
    changed = diff["changed"]
    total = len(added) + len(removed) + len(changed)

    if total == 1 and added:
        event = added[0]
        return (
            "Ny KOVA-aktivitet",
            f"{event['description']} • {event['dateLabel']} {event['time']}",
            "added",
        )

    if total == 1 and changed:
        item = changed[0]
        old = item["old"]
        new = item["new"]
        return (
            "KOVA-aktivitet endret",
            f"{new['description']}: {old['dateLabel']} {old['time']} → {new['dateLabel']} {new['time']}",
            "changed",
        )

    if total == 1 and removed:
        event = removed[0]
        return (
            "KOVA-aktivitet fjernet",
            f"{event['description']} • {event['dateLabel']} {event['time']}",
            "removed",
        )

    pieces = []
    if added:
        pieces.append(f"{len(added)} nye")
    if changed:
        pieces.append(f"{len(changed)} endret")
    if removed:
        pieces.append(f"{len(removed)} fjernet")

    return (
        f"KOVA oppdatert – {org['name']}",
        ", ".join(pieces),
        "summary",
    )


def send_diff_notification(org: dict, diff: dict, source_url: str) -> bool:
    total = len(diff["added"]) + len(diff["removed"]) + len(diff["changed"])
    if total == 0:
        return False

    credentials, project_id = _credentials()
    if credentials is None:
        print("FCM disabled: FIREBASE_SERVICE_ACCOUNT_JSON is not configured.")
        return False

    title, body, kind = _message_for(org, diff)
    endpoint = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"

    payload: dict[str, Any] = {
        "message": {
            "topic": topic_for(org["code"]),
            "notification": {
                "title": title,
                "body": body,
            },
            "data": {
                "title": title,
                "body": body,
                "kind": kind,
                "organization": org["code"],
                "sourceUrl": source_url,
            },
            "android": {
                "priority": "high",
                "notification": {
                    "channel_id": "kova_changes",
                },
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
    print(f"FCM sent to {topic_for(org['code'])}: {response.json().get('name', 'ok')}")
    return True
