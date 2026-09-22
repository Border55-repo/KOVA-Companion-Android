#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import requests
from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent
DATA_DIR = ROOT / "data"
DATA_DIR.mkdir(parents=True, exist_ok=True)

OSLO = ZoneInfo("Europe/Oslo")

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


def event_id(date_iso: str, time: str, type_name: str, description: str) -> str:
    raw = f"{date_iso}|{time}|{type_name}|{description}".encode("utf-8")
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
    return sorted(unique.values(), key=lambda item: (item["dateIso"], item["time"], item["description"]))


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


def read_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError):
        return None


def canonical_events(events: list[dict]) -> str:
    return json.dumps(events, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def write_snapshot(org: dict, events: list[dict], diff: dict) -> tuple[bool, dict]:
    path = DATA_DIR / f"{slug(org['code'])}.json"
    old = read_json(path)
    old_events = old.get("events", []) if old else []

    changed = canonical_events(old_events) != canonical_events(events)
    if not old:
        changed = True

    if changed:
        now_iso = datetime.now(OSLO).isoformat(timespec="seconds")
        payload = {
            "schemaVersion": 1,
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


def fetch_org(org: dict) -> tuple[list[dict], str]:
    url = source_url(org["code"])
    response = requests.get(
        url,
        timeout=20,
        headers={
            "User-Agent": "KOVA-Companion-Bridge/0.1 (+https://github.com/Border55-repo/KOVA-Companion-Android)"
        },
    )
    response.raise_for_status()
    return parse_schedule(response.text, url), url


def write_index(summaries: list[dict], changed_any: bool) -> None:
    path = DATA_DIR / "index.json"
    previous = read_json(path)
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
        "schemaVersion": 1,
        "bridge": "KOVA Companion Bridge",
        "updatedAt": datetime.now(OSLO).isoformat(timespec="seconds"),
        "organizations": stable_summary,
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    summaries = []
    changed_any = False
    failures = 0

    for org in ORGANIZATIONS:
        path = DATA_DIR / f"{slug(org['code'])}.json"
        old = read_json(path) or {}
        old_events = old.get("events", [])

        try:
            events, _ = fetch_org(org)
            diff = compute_diff(old_events, events)
            changed, payload = write_snapshot(org, events, diff)
            changed_any = changed_any or changed
            summaries.append(
                {
                    "name": org["name"],
                    "code": org["code"],
                    "file": path.name,
                    "status": "ok",
                    "eventCount": len(events),
                    "updatedAt": payload.get("updatedAt"),
                }
            )
            print(
                f"{org['code']}: {len(events)} events "
                f"(+{len(diff['added'])}/-{len(diff['removed'])}/~{len(diff['changed'])})"
            )
        except Exception as exc:
            failures += 1
            summaries.append(
                {
                    "name": org["name"],
                    "code": org["code"],
                    "file": path.name,
                    "status": "error",
                    "eventCount": len(old_events),
                    "updatedAt": old.get("updatedAt"),
                }
            )
            print(f"{org['code']}: ERROR {exc}", file=sys.stderr)

    write_index(summaries, changed_any or failures > 0)

    # Do not destroy a working bridge just because one organization was temporarily unavailable.
    return 1 if failures == len(ORGANIZATIONS) else 0


if __name__ == "__main__":
    raise SystemExit(main())
