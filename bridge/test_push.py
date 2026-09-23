#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

from push import FCM_SCOPE, topic_for
from reliability import change_id


def main() -> int:
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        print("FIREBASE_SERVICE_ACCOUNT_JSON is not configured.", file=sys.stderr)
        return 2

    info = json.loads(raw)
    project_id = info["project_id"]
    credentials = service_account.Credentials.from_service_account_info(
        info,
        scopes=[FCM_SCOPE],
    )
    credentials.refresh(Request())

    org = os.getenv("TEST_ORG", "UllensakerRKH")
    kind = os.getenv("TEST_KIND", "added").strip().lower() or "added"
    title = os.getenv("TEST_TITLE", f"KOVA Companion – {kind}-test")
    body = os.getenv(
        "TEST_BODY",
        f"FCM {kind}-test fra KOVA Bridge til Android.",
    )

    endpoint = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    event_id = os.getenv("TEST_EVENT_ID", f"smoke-test-{kind}-v1")
    event_data = {
        "id": event_id,
        "dateIso": "2026-09-23",
        "dateLabel": "ons. 23.9",
        "time": "18:30",
        "type": "Aktivitet",
        "description": "FCM smoke test",
        "sourceUrl": "https://www.kova.no/public/schedule.aspx?Organization=UllensakerRKH",
    }
    smoke_change_id = change_id(org, kind, event_data)

    payload = {
        "message": {
            "topic": topic_for(org),
            "data": {
                "title": title,
                "body": body,
                "kind": kind,
                "organization": org,
                "eventId": event_data["id"],
                "dateIso": event_data["dateIso"],
                "dateLabel": event_data["dateLabel"],
                "time": event_data["time"],
                "eventType": event_data["type"],
                "description": event_data["description"],
                "sourceUrl": event_data["sourceUrl"],
                "changeId": smoke_change_id,
            },
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
    print("FCM test sent successfully:", response.json().get("name", "ok"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
