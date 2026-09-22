#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import sys
import time
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import requests
from bs4 import BeautifulSoup

from push import send_diff_notification
from reliability import (
    append_history,
    change_records,
    enqueue_pending,
    load_push_state,
    mark_sent,
    pending_records,
    read_json,
    suspicious_snapshot,
)

ROOT = Path(__file__).resolve().parent
DATA_DIR = ROOT / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)
HISTORY_DIR = DATA_DIR / "history"
HISTORY_DIR.mkdir(parents=True, exist_ok=True)
PUSH_STATE_PATH = DATA_DIR / "push_state.json"
HEALTH_PATH = DATA_DIR / "health.json"

OSLO = ZoneInfo("Europe/Oslo")
HEALTH_HEARTBEAT_SECONDS = 3600

ORGANIZATIONS = [
    {"name": "Ullensaker Røde Kors Hjelpekorps", "code": "UllensakerRKH"},
    {"name": "Eidsvoll/Hurdal Røde Kors Hjelpekorps", "code": "EHRKH"},
    {"name": "Nittedal Røde Kors Hjelpekorps", "code": "Nittedal RKH"},
    {"name": "Skedsmo Røde Kors Hjelpekorps", "code": "Skedsmo RKH"},
]

KNOWN_TYPES = {
    "Aktivitet",
    "Ambulansevakt",
    "Båtvakt",
    "Beredskap",
    "Beredskapsvakt",
    "Dugnad",
    "Eksterne kurs",
    "Eksterne møter",
    "Forebygging",
    "Interne kurs",
    "Interne møter",
    "Korpskveld",
    "Øvelse",
    "Profilering",
    "Rådsmøte",
    "Sanitetsvakt",
    "Sommervakt",
    "Transport",
    "Vintervakt",
}

DATE_RE = re.compile(r"(\d{1,2})\.(\d{1,2})")
TIME_RE = re.compile(r"(?:->\s*)?(\d{1,2}:\d{2})$")


def slug(code: str) -> str:
    return code.replace(" ", "_")


def source_url(code: str) -> str:
    from urllib.parse import quote_plus
    return "https://www.kova.no/public/schedule.aspx?Organization=" + quote_plus(code)


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip().lower())


def event_semantic_key(event: dict) -> str:
    return normalize(event["type"]) + "|" + normalize(event["description"])


def event_id(date_iso: str, time_value: str, type_name: str, description: str) -> str:
    raw = f"{date_iso}|{time_value}|{type_name}|{description}".encode("utf-8")
    return hashlib.sha256(raw).hexdigest()[:16]


def parse_schedule(html: str, url: str, now: datetime | None = None) -> list[dict]:
    now = now or datetime.now(OSLO)
    soup = BeautifulSoup(html, "html.parser")
    events: list[dict] = []

    last_date_text: str | None = None
    year = now.year
    last_month = now.month

    for row in soup.select("tr"):
        cells = [
            re.sub(r"\s+", " ", cell.get_text(" ", strip=True)).strip()
            for cell in row.select("th,td")
        ]
        cells = [cell for cell in cells if cell]
        if not cells:
            continue

        type_index = next(
            (
                index
                for index, value in enumerate(cells)
                if any(value.casefold() == known.casefold() for known in KNOWN_TYPES)
            ),
            -1,
        )
        if type_index < 0:
            continue

        before_type = cells[:type_index]
        time_value = None
        for candidate in reversed(before_type):
            match = TIME_RE.fullmatch(candidate)
            if match:
                time_value = match.group(1)
                break
        if not time_value:
            continue

        for candidate in reversed(before_type):
            if DATE_RE.search(candidate):
                last_date_text = candidate
                break
        if not last_date_text:
            continue

        date_match = DATE_RE.search(last_date_text)
        if not date_match:
            continue

        day = int(date_match.group(1))
        month = int(date_match.group(2))
        if month < last_month - 6:
            year += 1
        last_month = month

        try:
            date_iso = f"{year:04d}-{month:02d}-{day:02d}"
            datetime.strptime(date_iso, "%Y-%m-%d")
        except ValueError:
            continue

        type_name = cells[type_index]
        description = " ".join(cells[type_index + 1 :]).strip() or type_name

        events.append(
            {
                "id": event_id(date_iso, time_value, type_name, description),
                "dateIso": date_iso,
                "dateLabel": last_date_text,
                "time": time_value,
                "type": type_name,
                "description": description,
                "sourceUrl": url,
            }
        )

    unique = {event["id"]: event for event in events}
    return sorted(
        unique.values(),
        key=lambda item: (item["dateIso"], item["time"], item["description"]),
    )


def compute_diff(old_events: list[dict], new_events: list[dict]) -> dict:
    old_by_key = {event_semantic_key(event): event for event in old_events}
    new_by_key = {event_semantic_key(event): event for event in new_events}

    changed = []
    for key in old_by_key.keys() & new_by_key.keys():
        old = old_by_key[key]
        new = new_by_key[key]
        if old["dateIso"] != new["dateIso"] or old["time"] != new["time"]:
            changed.append({"old": old, "new": new})

    added = [event for key, event in new_by_key.items() if key not in old_by_key]
    removed = [event for key, event in old_by_key.items() if key not in new_by_key]

    return {
        "added": sorted(added, key=lambda item: (item["dateIso"], item["time"])),
        "removed": sorted(removed, key=lambda item: (item["dateIso"], item["time"])),
        "changed": changed,
    }


def canonical_events(events: list[dict]) -> str:
    return json.dumps(events, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def write_snapshot(org: dict, events: list[dict], diff: dict) -> tuple[bool, dict]:
    path = DATA_DIR / f"{slug(org['code'])}.json"
    old = read_json(path, None)
    old_events = old.get("events", []) if old else []

    changed = canonical_events(old_events) != canonical_events(events)
    if not old:
        changed = True

    if changed:
        now_iso = datetime.now(OSLO).isoformat(timespec="seconds")
        payload = {
            "schemaVersion": 2,
            "organization": org,
            "status": "ok",
            "updatedAt": now_iso,
            "sourceUrl": source_url(org["code"]),
            "eventCount": len(events),
            "changes": {
                "added": len(diff["added"]),
                "removed": len(diff["removed"]),
                "changed": len(diff["changed"]),
            },
            "events": events,
        }
        path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        return True, payload

    return False, old


def fetch_org(org: dict, attempts: int = 3) -> tuple[list[dict], str, int]:
    url = source_url(org["code"])
    last_error: Exception | None = None

    for attempt in range(attempts):
        try:
            response = requests.get(
                url,
                timeout=20,
                headers={
                    "User-Agent": (
                        "KOVA-Companion-Bridge/0.7 "
                        "(+https://github.com/Border55-repo/KOVA-Companion-Android)"
                    )
                },
            )
            response.raise_for_status()
            html = response.text
            return parse_schedule(html, url), url, len(html)
        except (requests.RequestException, ValueError) as exc:
            last_error = exc
            if attempt < attempts - 1:
                delay = 2 ** attempt
                print(
                    f"{org['code']}: transient fetch error; retrying in {delay}s: {exc}",
                    file=sys.stderr,
                )
                time.sleep(delay)

    assert last_error is not None
    raise last_error


def write_index(summaries: list[dict], changed_any: bool) -> None:
    path = DATA_DIR / "index.json"
    previous = read_json(path, None)
    stable_summary = [
        {
            "name": item["name"],
            "code": item["code"],
            "file": item["file"],
            "status": item["status"],
            "eventCount": item["eventCount"],
            "updatedAt": item.get("updatedAt"),
        }
        for item in summaries
    ]

    previous_summary = previous.get("organizations", []) if previous else []
    if not changed_any and previous_summary == stable_summary:
        return

    payload = {
        "schemaVersion": 2,
        "bridge": "KOVA Companion Bridge",
        "updatedAt": datetime.now(OSLO).isoformat(timespec="seconds"),
        "organizations": stable_summary,
    }
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def records_to_diff(records: list[dict]) -> dict:
    added = []
    changed = []
    removed = []

    for record in records:
        kind = record.get("kind")
        if kind == "added":
            added.append(record["event"])
        elif kind == "changed":
            changed.append(
                {
                    "old": record.get("oldEvent", {}),
                    "new": record["event"],
                }
            )
        elif kind == "removed":
            removed.append(record["event"])

    return {"added": added, "changed": changed, "removed": removed}


def parse_iso(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value)
    except ValueError:
        return None


def write_health(
    summaries: list[dict],
    failures: int,
    changed_any: bool,
    push_count: int,
    pending_count: int,
) -> bool:
    now = datetime.now(OSLO)
    now_iso = now.isoformat(timespec="seconds")
    previous = read_json(HEALTH_PATH, {})

    status = (
        "error"
        if failures == len(ORGANIZATIONS)
        else "degraded"
        if failures
        else "ok"
    )

    previous_status = previous.get("status")
    previous_checked = parse_iso(previous.get("checkedAt"))
    heartbeat_due = (
        previous_checked is None
        or (now - previous_checked).total_seconds() >= HEALTH_HEARTBEAT_SECONDS
    )

    force_write = (
        changed_any
        or failures > 0
        or push_count > 0
        or pending_count > 0
        or status != previous_status
        or heartbeat_due
    )
    if not force_write:
        return False

    previous_failure_runs = int(previous.get("consecutiveFailureRuns", 0) or 0)
    consecutive_failure_runs = previous_failure_runs + 1 if failures else 0

    payload = {
        "schemaVersion": 1,
        "bridge": "KOVA Companion Bridge",
        "status": status,
        "checkedAt": now_iso,
        "lastSuccessfulRunAt": (
            now_iso if failures < len(ORGANIZATIONS) else previous.get("lastSuccessfulRunAt")
        ),
        "lastFullySuccessfulRunAt": (
            now_iso if failures == 0 else previous.get("lastFullySuccessfulRunAt")
        ),
        "consecutiveFailureRuns": consecutive_failure_runs,
        "pendingPushes": pending_count,
        "lastPushAt": (
            now_iso if push_count > 0 else previous.get("lastPushAt")
        ),
        "lastPushCount": push_count if push_count > 0 else previous.get("lastPushCount", 0),
        "organizations": summaries,
    }

    HEALTH_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return True


def main() -> int:
    summaries = []
    changed_any = False
    failures = 0
    total_pushes = 0

    push_state = load_push_state(PUSH_STATE_PATH)

    for org in ORGANIZATIONS:
        path = DATA_DIR / f"{slug(org['code'])}.json"
        old = read_json(path, {})
        old_events = old.get("events", [])
        error_text = None

        try:
            events, _, html_length = fetch_org(org)

            suspect = suspicious_snapshot(
                old_count=len(old_events),
                new_count=len(events),
                html_length=html_length,
            )
            if suspect:
                raise RuntimeError(suspect)

            diff = compute_diff(old_events, events)
            records = change_records(org, diff)

            if records:
                append_history(
                    HISTORY_DIR / f"{slug(org['code'])}.json",
                    records,
                )
                push_state, _ = enqueue_pending(PUSH_STATE_PATH, push_state, records)

            changed, payload = write_snapshot(org, events, diff)
            changed_any = changed_any or changed

            pending_for_org = [
                item
                for item in pending_records(push_state)
                if item.get("organization") == org["code"]
            ]

            if old and pending_for_org:
                pending_diff = records_to_diff(pending_for_org)
                sent_ids = send_diff_notification(
                    org,
                    pending_diff,
                    source_url(org["code"]),
                )
                if sent_ids:
                    total_pushes += len(sent_ids)
                    mark_sent(PUSH_STATE_PATH, push_state, sent_ids)
                    push_state = load_push_state(PUSH_STATE_PATH)

            summaries.append(
                {
                    "name": org["name"],
                    "code": org["code"],
                    "file": path.name,
                    "status": "ok",
                    "eventCount": len(events),
                    "updatedAt": payload.get("updatedAt"),
                    "error": None,
                }
            )
            print(
                f"{org['code']}: {len(events)} events "
                f"(+{len(diff['added'])}/-{len(diff['removed'])}/~{len(diff['changed'])}) "
                f"pending={len([x for x in pending_records(push_state) if x.get('organization') == org['code']])}"
            )
        except Exception as exc:
            failures += 1
            error_text = str(exc)[:240]
            summaries.append(
                {
                    "name": org["name"],
                    "code": org["code"],
                    "file": path.name,
                    "status": "error",
                    "eventCount": len(old_events),
                    "updatedAt": old.get("updatedAt"),
                    "error": error_text,
                }
            )
            print(f"{org['code']}: ERROR {error_text}", file=sys.stderr)

    write_index(summaries, changed_any or failures > 0)

    remaining_pending = len(pending_records(push_state))
    health_written = write_health(
        summaries=summaries,
        failures=failures,
        changed_any=changed_any,
        push_count=total_pushes,
        pending_count=remaining_pending,
    )

    print(
        f"Bridge summary: failures={failures}, pushes={total_pushes}, "
        f"pending={remaining_pending}, health_written={health_written}"
    )

    return 1 if failures == len(ORGANIZATIONS) else 0


if __name__ == "__main__":
    raise SystemExit(main())
