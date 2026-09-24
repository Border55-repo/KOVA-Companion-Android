#!/usr/bin/env python3
from __future__ import annotations
import json, os, time
from pathlib import Path
from push import _credentials, _send
from webpush import send_web_notification

TITLE = os.getenv("ANNOUNCEMENT_TITLE", "Nytt i KOVA Companion")
BODY = os.getenv("ANNOUNCEMENT_BODY", "Nye forbedringer er tilgjengelige.")
CHANGE_ID = os.getenv("ANNOUNCEMENT_ID", f"announcement-{int(time.time())}")

def main():
    credentials, project_id = _credentials()
    if credentials is None:
        raise SystemExit("Firebase is not configured")
    registry = json.loads(Path("bridge/data/organizations.json").read_text(encoding="utf-8"))
    organizations = registry.get("organizations", [])
    # Existing Android versions subscribe per corps, so announce on every known corps topic.
    for org in organizations:
        _send(credentials, project_id, org, TITLE, BODY, "announcement", "https://border55-repo.github.io/KlarX/kova/", None, CHANGE_ID)
    # PWA subscriptions are filtered by corps. Sending per corps reaches all existing subscriptions.
    for org in organizations:
        send_web_notification(org["code"], TITLE, BODY, "announcement", None, CHANGE_ID)
    print(f"Announcement dispatched across {len(organizations)} KOVA organizations.")

if __name__ == "__main__":
    main()
