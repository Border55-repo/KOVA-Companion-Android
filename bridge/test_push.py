#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

from push import FCM_SCOPE, topic_for


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
    title = os.getenv("TEST_TITLE", "KOVA Companion – push test")
    body = os.getenv(
        "TEST_BODY",
        "Ekte Firebase-push fungerer fra KOVA Bridge til Android.",
    )

    endpoint = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    payload = {
        "message": {
            "topic": topic_for(org),
            "notification": {
                "title": title,
                "body": body,
            },
            "data": {
                "title": title,
                "body": body,
                "kind": "test",
                "organization": org,
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
    print("FCM test sent successfully:", response.json().get("name", "ok"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
