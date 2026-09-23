from __future__ import annotations

import os
import time

from webpush import send_web_notification


def main():
    org = os.getenv("TEST_ORG", "UllensakerRKH")
    change_id = os.getenv("TEST_CHANGE_ID", "").strip() or f"pwa-smoke-{int(time.time())}"
    event = {
        "id": "pwa-smoke-event",
        "dateIso": "2026-09-23",
        "dateLabel": "23.09.2026",
        "time": "12:00",
        "type": "Test",
        "description": "PWA-varseltest",
        "sourceUrl": "https://www.kova.no/",
    }
    ok = send_web_notification(
        org,
        "KOVA Companion – iPhone test",
        "Web Push fungerer for KOVA Companion.",
        "added",
        event,
        change_id,
    )
    if not ok:
        raise SystemExit("Web Push smoke test had a transient delivery failure")
    print(f"Web Push smoke test completed for {org} ({change_id}).")


if __name__ == "__main__":
    main()
