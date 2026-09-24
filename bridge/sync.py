#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import time
from datetime import datetime
from pathlib import Path
from urllib.parse import parse_qs, quote_plus, unquote_plus, urlparse
from zoneinfo import ZoneInfo

import requests
import firebase_admin
from firebase_admin import credentials, firestore
from bs4 import BeautifulSoup

from push import send_diff_notification
from webpush import send_due_reminders
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
ORGANIZATIONS_PATH = DATA_DIR / "organizations.json"
EVENTS_URL = "https://www.kova.no/Events.aspx"

OSLO = ZoneInfo("Europe/Oslo")
HEALTH_HEARTBEAT_SECONDS = 3600
DISCOVERY_USER_AGENT = (
    "KOVA-Companion-Bridge/0.8 "
    "(+https://github.com/Border55-repo/KOVA-Companion-Android)"
)

FALLBACK_ORGANIZATIONS = [
    {"name": "Ullensaker Røde Kors Hjelpekorps", "code": "UllensakerRKH", "category": "hjelpekorps"},
    {"name": "Eidsvoll og Hurdal Røde Kors Hjelpekorps", "code": "EHRKH", "category": "hjelpekorps"},
    {"name": "Nittedal Røde Kors Hjelpekorps", "code": "Nittedal RKH", "category": "hjelpekorps"},
    {"name": "Skedsmo Røde Kors Hjelpekorps", "code": "Skedsmo RKH", "category": "hjelpekorps"},
]

# These remain fast because they are the original production set.
PRIORITY_CODES = {item["code"] for item in FALLBACK_ORGANIZATIONS}
NON_PRIORITY_BUCKETS = 4

KNOWN_TYPES = {
    "Aksjon",
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
    "RØFF",
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
_ADMIN_DB = None


def admin_db():
    global _ADMIN_DB
    if _ADMIN_DB is not None:
        return _ADMIN_DB

    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        return None

    try:
        info = json.loads(raw)
        if not firebase_admin._apps:
            firebase_admin.initialize_app(
                credentials.Certificate(info),
                {"projectId": info.get("project_id", "kova-companion")},
            )
        _ADMIN_DB = firestore.client()
        return _ADMIN_DB
    except Exception as exc:
        print(f"Admin runtime unavailable: {exc}", file=sys.stderr)
        return None


def read_bridge_sync_request(db):
    if db is None:
        return None
    try:
        snap = db.collection("adminCommands").document("bridgeSync").get()
        if not snap.exists:
            return None
        data = snap.to_dict() or {}
        return data if data.get("status") == "requested" else None
    except Exception as exc:
        print(f"Could not read admin sync request: {exc}", file=sys.stderr)
        return None


def set_bridge_sync_command(db, command: dict | None, status: str, **extra) -> None:
    if db is None or not command:
        return
    try:
        payload = dict(command)
        payload.update(extra)
        payload["action"] = "bridgeSync"
        payload["status"] = status
        db.collection("adminCommands").document("bridgeSync").set(payload)
    except Exception as exc:
        print(f"Could not update admin sync request: {exc}", file=sys.stderr)



def process_announcement_request(db) -> None:
    if db is None:
        return
    ref = db.collection("adminCommands").document("announcement")
    try:
        snap = ref.get()
        if not snap.exists:
            return
        command = snap.to_dict() or {}
        if command.get("status") != "requested":
            return
        from broadcast_announcement import dispatch_announcement
        ref.set({**command, "status": "running", "startedAt": firestore.SERVER_TIMESTAMP})
        dispatch_announcement(
            title=str(command.get("title") or "Nytt i KOVA Companion"),
            body=str(command.get("body") or "Nye forbedringer er tilgjengelige."),
            change_id=str(command.get("requestId") or f"announcement-{int(time.time())}"),
        )
        ref.set({**command, "status": "completed", "completedAt": firestore.SERVER_TIMESTAMP})
    except Exception as exc:
        print(f"Could not process announcement request: {exc}", file=sys.stderr)
        try:
            current = ref.get().to_dict() or {}
            ref.set({**current, "status": "failed", "error": str(exc), "completedAt": firestore.SERVER_TIMESTAMP})
        except Exception:
            pass

def publish_admin_runtime(
    db,
    organizations: list[dict],
    summaries: list[dict],
    failures: int,
    pushes: int,
    pending: int,
    full_sync: bool,
) -> None:
    if db is None:
        return
    try:
        ref = db.collection("adminRuntime").document("bridge")
        previous = ref.get().to_dict() or {}
        checks = dict(previous.get("organizationChecks") or {})
        now_iso = datetime.now(OSLO).isoformat(timespec="seconds")

        for item in summaries:
            checks[slug(item["code"])] = {
                "name": item["name"],
                "code": item["code"],
                "status": item.get("status", "unknown"),
                "eventCount": int(item.get("eventCount", 0) or 0),
                "checkedAt": now_iso,
                "error": item.get("error"),
            }

        status = (
            "error"
            if summaries and failures == len(summaries)
            else "degraded"
            if failures
            else "ok"
        )

        ref.set(
            {
                "status": status,
                "runtimeStartedAt": previous.get("runtimeStartedAt") or now_iso,
                "lastRunAt": now_iso,
                "fullSync": bool(full_sync),
                "organizationCount": len(organizations),
                "helpCorpsCount": sum(
                    1
                    for item in organizations
                    if item.get("category") == "hjelpekorps"
                ),
                "polledThisRun": len(summaries),
                "failures": failures,
                "pushesThisRun": pushes,
                "pendingPushes": pending,
                "organizationChecks": checks,
            }
        )
    except Exception as exc:
        print(f"Could not publish admin runtime: {exc}", file=sys.stderr)


def slug(code: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", code).strip("_")


def source_url(code: str) -> str:
    return "https://www.kova.no/public/schedule.aspx?Organization=" + quote_plus(code)


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip().lower())


def classify_organization(name: str) -> str:
    folded = name.casefold()
    if "hjelpekorps" in folded:
        return "hjelpekorps"
    if "ambulanse" in folded:
        return "ambulanse"
    if "omsorg" in folded:
        return "omsorg"
    if "båt" in folded or "båten" in folded:
        return "bat"
    if "lokalforening" in folded:
        return "lokalforening"
    return "annet"


def discover_organizations(attempts: int = 3) -> list[dict]:
    last_error: Exception | None = None

    for attempt in range(attempts):
        try:
            response = requests.get(
                EVENTS_URL,
                timeout=20,
                headers={"User-Agent": DISCOVERY_USER_AGENT},
            )
            response.raise_for_status()
            soup = BeautifulSoup(response.text, "html.parser")

            found: dict[str, dict] = {}
            for anchor in soup.find_all("a", href=True):
                href = anchor.get("href", "")
                if "schedule.aspx" not in href or "Organization=" not in href:
                    continue

                parsed = urlparse(href)
                query = parse_qs(parsed.query)
                code_values = query.get("Organization")
                if not code_values:
                    continue

                code = unquote_plus(code_values[0]).strip()
                name = re.sub(r"\s+", " ", anchor.get_text(" ", strip=True)).strip()
                if not code or not name:
                    continue

                found[code] = {
                    "name": name,
                    "code": code,
                    "category": classify_organization(name),
                }

            if len(found) < 4:
                raise RuntimeError(
                    f"KOVA organization discovery returned only {len(found)} entries"
                )

            return sorted(
                found.values(),
                key=lambda item: (
                    0 if item["category"] == "hjelpekorps" else 1,
                    item["name"].casefold(),
                ),
            )
        except (requests.RequestException, RuntimeError) as exc:
            last_error = exc
            if attempt < attempts - 1:
                delay = 2 ** attempt
                print(
                    f"Organization discovery retry in {delay}s: {exc}",
                    file=sys.stderr,
                )
                time.sleep(delay)

    cached = read_json(ORGANIZATIONS_PATH, {})
    cached_orgs = cached.get("organizations", []) if isinstance(cached, dict) else []
    if cached_orgs:
        print(
            f"Organization discovery failed; using {len(cached_orgs)} cached entries: {last_error}",
            file=sys.stderr,
        )
        return cached_orgs

    print(
        f"Organization discovery failed; using fallback list: {last_error}",
        file=sys.stderr,
    )
    return FALLBACK_ORGANIZATIONS


def write_organization_registry(organizations: list[dict]) -> bool:
    previous = read_json(ORGANIZATIONS_PATH, {})
    previous_orgs = previous.get("organizations", []) if isinstance(previous, dict) else []
    if previous_orgs == organizations:
        return False

    payload = {
        "schemaVersion": 1,
        "source": EVENTS_URL,
        "updatedAt": datetime.now(OSLO).isoformat(timespec="seconds"),
        "count": len(organizations),
        "helpCorpsCount": sum(
            1 for item in organizations if item.get("category") == "hjelpekorps"
        ),
        "organizations": organizations,
    }
    ORGANIZATIONS_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return True


def poll_batch(organizations: list[dict], now: datetime | None = None) -> list[dict]:
    now = now or datetime.now(OSLO)
    bucket = int(now.timestamp() // 300) % NON_PRIORITY_BUCKETS
    selected = []

    for org in organizations:
        code = org["code"]
        if code in PRIORITY_CODES:
            selected.append(org)
            continue

        digest = int(hashlib.sha256(code.encode("utf-8")).hexdigest()[:8], 16)
        if digest % NON_PRIORITY_BUCKETS == bucket:
            selected.append(org)

    return selected


def event_semantic_key(event: dict) -> str:
    return normalize(event["type"]) + "|" + normalize(event["description"])


def normalized_time(value: str) -> str:
    match = re.search(r"\d{1,2}:\d{2}", value or "")
    return match.group(0) if match else ""


def event_id(date_iso: str, time_value: str, type_name: str, description: str) -> str:
    raw = f"{date_iso}|{normalized_time(time_value)}|{type_name}|{description}".encode("utf-8")
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
                time_value = candidate.strip()
                break
        if time_value is None:
            time_value = ""

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
        if old["dateIso"] != new["dateIso"] or normalized_time(old["time"]) != normalized_time(new["time"]):
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
            "schemaVersion": 3,
            "apiVersion": "2.0",
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
                headers={"User-Agent": DISCOVERY_USER_AGENT},
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


def write_index(organizations: list[dict], summaries: list[dict], changed_any: bool) -> None:
    path = DATA_DIR / "index.json"
    previous = read_json(path, {})
    summary_by_code = {item["code"]: item for item in summaries}

    rows = []
    for org in organizations:
        previous_row = next(
            (
                item
                for item in previous.get("organizations", [])
                if item.get("code") == org["code"]
            ),
            {},
        )
        current = summary_by_code.get(org["code"])
        rows.append(
            {
                "name": org["name"],
                "code": org["code"],
                "category": org.get("category", "annet"),
                "file": f"{slug(org['code'])}.json",
                "status": (
                    current.get("status")
                    if current
                    else previous_row.get("status", "pending")
                ),
                "eventCount": (
                    current.get("eventCount")
                    if current
                    else previous_row.get("eventCount", 0)
                ),
                "updatedAt": (
                    current.get("updatedAt")
                    if current
                    else previous_row.get("updatedAt")
                ),
            }
        )

    old_rows = previous.get("organizations", [])
    if not changed_any and old_rows == rows:
        return

    payload = {
        "schemaVersion": 4,
        "apiVersion": "2.0",
        "bridge": "KOVA Companion Bridge",
        "capabilities": {
            "multiCorps": True,
            "offlineSnapshots": True,
            "eventChangeTracking": True,
            "webPushPreferences": True,
            "scheduledReminders": True,
            "adminHealth": True,
        },
        "updatedAt": datetime.now(OSLO).isoformat(timespec="seconds"),
        "organizationCount": len(organizations),
        "helpCorpsCount": sum(
            1 for item in organizations if item.get("category") == "hjelpekorps"
        ),
        "organizations": rows,
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
    organizations: list[dict],
    polled: list[dict],
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
        if failures == len(polled) and polled
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
        or previous.get("organizationCount") != len(organizations)
    )
    if not force_write:
        return False

    previous_failure_runs = int(previous.get("consecutiveFailureRuns", 0) or 0)
    consecutive_failure_runs = previous_failure_runs + 1 if failures else 0

    payload = {
        "schemaVersion": 3,
        "apiVersion": "2.0",
        "bridge": "KOVA Companion Bridge",
        "capabilities": {
            "snapshotFallback": True,
            "pushQueue": True,
            "webPush": True,
            "fcm": True,
        },
        "status": status,
        "checkedAt": now_iso,
        "lastSuccessfulRunAt": (
            now_iso if failures < len(polled) else previous.get("lastSuccessfulRunAt")
        ),
        "lastFullySuccessfulRunAt": (
            now_iso if failures == 0 else previous.get("lastFullySuccessfulRunAt")
        ),
        "consecutiveFailureRuns": consecutive_failure_runs,
        "pendingPushes": pending_count,
        "lastPushAt": (
            now_iso if push_count > 0 else previous.get("lastPushAt")
        ),
        "lastPushCount": (
            push_count if push_count > 0 else previous.get("lastPushCount", 0)
        ),
        "organizationCount": len(organizations),
        "helpCorpsCount": sum(
            1 for item in organizations if item.get("category") == "hjelpekorps"
        ),
        "polledThisRun": len(polled),
        "organizations": summaries,
    }

    HEALTH_PATH.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return True


def main() -> int:
    organizations = discover_organizations()
    registry_changed = write_organization_registry(organizations)

    db = admin_db()
    process_announcement_request(db)
    sync_command = read_bridge_sync_request(db)
    force_full_sync = bool(sync_command)
    if force_full_sync:
        set_bridge_sync_command(
            db,
            sync_command,
            "running",
            startedAt=datetime.now(OSLO).isoformat(timespec="seconds"),
        )
        polled = organizations
        print("Admin requested full Bridge sync.")
    else:
        polled = poll_batch(organizations)

    print(
        f"Discovered {len(organizations)} public KOVA organizations "
        f"({sum(1 for x in organizations if x.get('category') == 'hjelpekorps')} hjelpekorps); "
        f"polling {len(polled)} this run."
    )

    summaries = []
    changed_any = registry_changed
    failures = 0
    total_pushes = 0
    push_state = load_push_state(PUSH_STATE_PATH)

    for org in polled:
        path = DATA_DIR / f"{slug(org['code'])}.json"
        old = read_json(path, {})
        old_events = old.get("events", [])

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

            # First snapshot is a baseline, not a real change. Never notify or audit
            # pre-existing calendar entries as newly added during onboarding.
            if old and records:
                append_history(
                    HISTORY_DIR / f"{slug(org['code'])}.json",
                    records,
                )
                push_state, _ = enqueue_pending(
                    PUSH_STATE_PATH,
                    push_state,
                    records,
                )

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
                    "category": org.get("category", "annet"),
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
                    "category": org.get("category", "annet"),
                    "file": path.name,
                    "status": "error",
                    "eventCount": len(old_events),
                    "updatedAt": old.get("updatedAt"),
                    "error": error_text,
                }
            )
            print(f"{org['code']}: ERROR {error_text}", file=sys.stderr)

    write_index(
        organizations,
        summaries,
        changed_any or failures > 0,
    )

    reminder_events = {}
    for organization in organizations:
        payload = read_json(
            DATA_DIR / f"{slug(organization['code'])}.json",
            {},
        )
        reminder_events[organization["code"]] = payload.get("events", [])

    total_pushes += send_due_reminders(reminder_events)

    remaining_pending = len(pending_records(push_state))
    health_written = write_health(
        organizations=organizations,
        polled=polled,
        summaries=summaries,
        failures=failures,
        changed_any=changed_any,
        push_count=total_pushes,
        pending_count=remaining_pending,
    )

    publish_admin_runtime(
        db=db,
        organizations=organizations,
        summaries=summaries,
        failures=failures,
        pushes=total_pushes,
        pending=remaining_pending,
        full_sync=force_full_sync,
    )

    if force_full_sync:
        set_bridge_sync_command(
            db,
            sync_command,
            "completed" if failures < len(polled) else "failed",
            completedAt=datetime.now(OSLO).isoformat(timespec="seconds"),
            failures=failures,
            polled=len(polled),
        )

    print(
        f"Bridge summary: discovered={len(organizations)}, polled={len(polled)}, "
        f"failures={failures}, pushes={total_pushes}, pending={remaining_pending}, "
        f"health_written={health_written}, full_sync={force_full_sync}"
    )

    return 1 if polled and failures == len(polled) else 0


if __name__ == "__main__":
    raise SystemExit(main())
