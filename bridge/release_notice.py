"""Publish a reviewed release notice from GitHub Actions, without the Admin UI."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path

import requests

NOTICE_PATH = Path(__file__).with_name("release-notice.json")
LIVE_URL = "https://border55-repo.github.io/KOVA-Companion-Android/"


def load_notice(path: Path = NOTICE_PATH) -> dict[str, str]:
    notice = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(notice, dict) or set(notice) != {"id", "version", "title", "body"}:
        raise ValueError("Release notice must contain id, version, title and body only")
    if not all(isinstance(value, str) for value in notice.values()):
        raise ValueError("Release notice fields must be text")
    if not re.fullmatch(r"kova-[0-9]{8}-[a-z0-9]{2,20}", notice["id"]):
        raise ValueError("Invalid release notice ID")
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+", notice["version"]):
        raise ValueError("Invalid PWA version")
    if not 3 <= len(notice["title"].strip()) <= 80:
        raise ValueError("Title must contain 3–80 characters")
    if not 10 <= len(notice["body"].strip()) <= 180:
        raise ValueError("Body must contain 10–180 characters")
    if any(notice[key] != notice[key].strip() for key in ("title", "body")):
        raise ValueError("Title and body must not have leading or trailing spaces")
    return notice


def notice_digest(notice: dict[str, str]) -> str:
    encoded = json.dumps(notice, ensure_ascii=False, sort_keys=True).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def verify_live_version(version: str) -> None:
    response = requests.get(LIVE_URL, headers={"Cache-Control": "no-cache"}, timeout=20)
    response.raise_for_status()
    if f"Webapp · Android og iPhone · {version}" not in response.text:
        raise RuntimeError(f"PWA {version} is not live; release notice was not sent")


def firebase_client():
    import firebase_admin
    from firebase_admin import credentials, firestore

    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON is not configured")
    info = json.loads(raw)
    firebase_admin.initialize_app(credentials.Certificate(info))
    return firestore.client()


class ReleaseDeliveryError(RuntimeError):
    def __init__(self, delivery: dict):
        super().__init__("PWA release push was not fully accepted")
        self.delivery = delivery


def send_pwa_notice(title: str, body: str, notice_id: str, *, audience: str) -> dict:
    from webpush import send_web_announcement

    if audience != "all":
        raise ValueError("Release notices must target all PWA users")
    delivery = {"pwa": send_web_announcement(title, body, notice_id, audience="all")}
    if delivery["pwa"]["failed"] or not delivery["pwa"]["targets"]:
        raise ReleaseDeliveryError(delivery)
    return delivery


def publish_notice(notice: dict[str, str], db, deliver) -> dict:
    from google.cloud.exceptions import Conflict

    digest = notice_digest(notice)
    ledger = db.collection("releaseAnnouncements").document(notice["id"])
    try:
        ledger.create({
            "status": "running", "manifestSha256": digest,
            "version": notice["version"], "startedAt": datetime.now(timezone.utc),
        })
    except Conflict:
        previous = ledger.get().to_dict() or {}
        if previous.get("manifestSha256") != digest:
            raise RuntimeError("Release notice ID was reused with different content")
        if previous.get("status") == "completed":
            print("Release notice already completed; no duplicate push sent.")
            return previous.get("delivery") or {}
        raise RuntimeError("Release notice already started; inspect delivery before retrying")

    try:
        published_at = datetime.now(timezone.utc)
        batch = db.batch()
        batch.set(db.collection("changelog").document(notice["id"]), {
            "id": notice["id"], "title": notice["title"], "body": notice["body"],
            "publishedAt": published_at, "publishedBy": "release-workflow", "sendPush": True,
        })
        batch.set(db.collection("publicConfig").document("changelog"), {
            "latestId": notice["id"], "title": notice["title"],
            "body": notice["body"], "updatedAt": published_at,
        })
        batch.commit()
        delivery = deliver(notice["title"], notice["body"], notice["id"], audience="all")
    except Exception as exc:
        ledger.update({
            "status": "failed", "errorType": type(exc).__name__,
            "delivery": getattr(exc, "delivery", {}),
            "completedAt": datetime.now(timezone.utc),
        })
        raise

    ledger.update({
        "status": "completed", "delivery": delivery,
        "completedAt": datetime.now(timezone.utc),
    })
    print("Release notice delivery: " + json.dumps(delivery, sort_keys=True))
    return delivery


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--validate", action="store_true")
    args = parser.parse_args()
    notice = load_notice()
    if args.validate:
        print(f"Release notice {notice['id']} is valid.")
        return
    verify_live_version(notice["version"])
    publish_notice(notice, firebase_client(), send_pwa_notice)


if __name__ == "__main__":
    main()
