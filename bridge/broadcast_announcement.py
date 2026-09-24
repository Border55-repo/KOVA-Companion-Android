#!/usr/bin/env python3
from __future__ import annotations
import json, os, time
from pathlib import Path
from push import _credentials, _send
from webpush import send_web_notification, send_web_announcement

TITLE = os.getenv("ANNOUNCEMENT_TITLE", "Nytt i KOVA Companion")
BODY = os.getenv("ANNOUNCEMENT_BODY", "Nye forbedringer er tilgjengelige.")
CHANGE_ID = os.getenv("ANNOUNCEMENT_ID", f"announcement-{int(time.time())}")

def dispatch_announcement(title: str = TITLE, body: str = BODY, change_id: str = CHANGE_ID):
    credentials, project_id = _credentials()
    if credentials is None:
        raise SystemExit("Firebase is not configured")
    registry = json.loads(Path("bridge/data/organizations.json").read_text(encoding="utf-8"))
    organizations = registry.get("organizations", [])
    # Existing Android versions subscribe per corps, so announce on every known corps topic.
    for org in organizations:
        _send(credentials, project_id, org, title, body, "announcement", "https://border55-repo.github.io/KlarX/kova/", None, change_id)
    # Announcements are global product messages: send once to every enabled PWA subscription.\n    send_web_announcement(title, body, change_id)
    print(f"Announcement dispatched across {len(organizations)} KOVA organizations.")

def main():
    dispatch_announcement()

if __name__ == "__main__":
    main()
