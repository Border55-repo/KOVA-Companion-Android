#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import time
from pathlib import Path

from push import _credentials, _send
from webpush import send_web_announcement

TITLE = os.getenv("ANNOUNCEMENT_TITLE", "Nytt i Kova Companion")
BODY = os.getenv("ANNOUNCEMENT_BODY", "Nye forbedringer er tilgjengelige.")
CHANGE_ID = os.getenv("ANNOUNCEMENT_ID", f"announcement-{int(time.time())}")
REGISTRY_PATH = Path(__file__).resolve().parent / "data" / "organizations.json"


class AnnouncementDeliveryError(RuntimeError):
    def __init__(self, delivery):
        super().__init__("Utsending feilet helt eller delvis. Se status for Android og PWA.")
        self.delivery = delivery


def dispatch_announcement(title: str = TITLE, body: str = BODY, change_id: str = CHANGE_ID):
    # Attempt both transports independently: an FCM failure must not suppress iPhone.
    delivery = {"android": {"targets": 0, "accepted": 0, "failed": 0}}
    android = delivery["android"]
    try:
        credentials, project_id = _credentials()
        if credentials is None:
            raise RuntimeError("Firebase is not configured")
        organizations = json.loads(REGISTRY_PATH.read_text(encoding="utf-8")).get("organizations", [])
        if not organizations:
            raise RuntimeError("Organization registry is empty")
        organizations = [{"code": "all_users"}, *organizations]
        android["targets"] = len(organizations)
        # Per-corps topics support existing Android versions. changeId deduplicates.
        for org in organizations:
            try:
                _send(credentials, project_id, org, title, body, "announcement",
                      "https://border55-repo.github.io/KOVA-Companion-Android/#changelogCard", None, change_id)
                android["accepted"] += 1
            except Exception:
                android["failed"] += 1
    except Exception:
        android["failed"] += 1

    try:
        delivery["pwa"] = send_web_announcement(title, body, change_id)
    except Exception:
        delivery["pwa"] = {"targets": 0, "accepted": 0, "failed": 1, "expired": 0}

    print("Announcement transport results: " + json.dumps(delivery), flush=True)
    if any(result["failed"] for result in delivery.values()):
        raise AnnouncementDeliveryError(delivery)
    return delivery


def main():
    dispatch_announcement()


if __name__ == "__main__":
    main()
