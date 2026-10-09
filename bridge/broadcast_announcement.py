#!/usr/bin/env python3
"""Send Admin announcements to PWA subscriptions."""
from __future__ import annotations

import json
import os
import re
import time
from pathlib import Path

from webpush import send_web_announcement

TITLE = os.getenv("ANNOUNCEMENT_TITLE", "Nytt i Kova Companion")
BODY = os.getenv("ANNOUNCEMENT_BODY", "Nye forbedringer er tilgjengelige.")
CHANGE_ID = os.getenv("ANNOUNCEMENT_ID", f"announcement-{int(time.time())}")
REGISTRY_PATH = Path(__file__).resolve().parent / "data" / "organizations.json"


class AnnouncementDeliveryError(RuntimeError):
    def __init__(self, delivery):
        super().__init__("PWA-utsending feilet helt eller delvis. Se leveringsstatus.")
        self.delivery = delivery


def dispatch_announcement(title: str = TITLE, body: str = BODY, change_id: str = CHANGE_ID,
                          audience="all", organization="", subscription_id=""):
    if audience not in ("all", "organization", "test"):
        raise ValueError("Invalid announcement audience")
    if audience == "test" and not re.fullmatch(r"[a-f0-9]{64}", subscription_id):
        raise ValueError("A test requires exactly one subscription ID")
    if audience == "organization":
        registry = json.loads(REGISTRY_PATH.read_text(encoding="utf-8")).get("organizations", [])
        if not organization or not any(o["code"] == organization for o in registry):
            raise ValueError("Unknown organization")

    kwargs = {} if audience == "all" else {"audience": audience}
    if audience == "organization":
        kwargs["organization"] = organization
    if audience == "test":
        kwargs["subscription_id"] = subscription_id
    try:
        result = send_web_announcement(title, body, change_id, **kwargs)
    except Exception:
        result = {"targets": 0, "accepted": 0, "failed": 1, "expired": 0}
    delivery = {"pwa": result}
    print("Announcement transport results: " + json.dumps(delivery), flush=True)
    if result["failed"] or (audience == "test" and (result["targets"] != 1 or result["accepted"] != 1)):
        raise AnnouncementDeliveryError(delivery)
    return delivery


def main():
    dispatch_announcement()


if __name__ == "__main__":
    main()

