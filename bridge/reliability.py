from __future__ import annotations

import hashlib
import json
import re
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

OSLO = ZoneInfo("Europe/Oslo")


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", (value or "").strip().lower())


def change_id(
    organization: str,
    kind: str,
    event: dict,
    old_event: dict | None = None,
) -> str:
    old_event = old_event or {}
    if kind == "removed":
        new_date = ""
        new_time = ""
        old_date = str(event.get("dateIso", ""))
        old_time = str(event.get("time", ""))
    else:
        new_date = str(event.get("dateIso", ""))
        new_time = str(event.get("time", ""))
        old_date = str(old_event.get("dateIso", ""))
        old_time = str(old_event.get("time", ""))

    raw = "|".join(
        [
            organization,
            kind,
            normalize(str(event.get("type", ""))),
            normalize(str(event.get("description", ""))),
            old_date,
            old_time,
            new_date,
            new_time,
        ]
    )
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def change_records(org: dict, diff: dict, observed_at: str | None = None) -> list[dict]:
    observed_at = observed_at or datetime.now(OSLO).isoformat(timespec="seconds")
    records: list[dict] = []

    for event in diff.get("added", []):
        records.append(
            {
                "changeId": change_id(org["code"], "added", event),
                "observedAt": observed_at,
                "organization": org["code"],
                "kind": "added",
                "event": event,
            }
        )

    for item in diff.get("changed", []):
        old = item["old"]
        new = item["new"]
        records.append(
            {
                "changeId": change_id(org["code"], "changed", new, old),
                "observedAt": observed_at,
                "organization": org["code"],
                "kind": "changed",
                "event": new,
                "oldEvent": old,
            }
        )

    for event in diff.get("removed", []):
        records.append(
            {
                "changeId": change_id(org["code"], "removed", event),
                "observedAt": observed_at,
                "organization": org["code"],
                "kind": "removed",
                "event": event,
            }
        )

    return records


def filter_diff_by_change_ids(diff: dict, allowed_ids: set[str], organization: str) -> dict:
    added = [
        event
        for event in diff.get("added", [])
        if change_id(organization, "added", event) in allowed_ids
    ]
    changed = [
        item
        for item in diff.get("changed", [])
        if change_id(organization, "changed", item["new"], item["old"]) in allowed_ids
    ]
    removed = [
        event
        for event in diff.get("removed", [])
        if change_id(organization, "removed", event) in allowed_ids
    ]
    return {"added": added, "changed": changed, "removed": removed}


def read_json(path: Path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return default


def append_history(path: Path, records: list[dict], max_records: int = 500) -> bool:
    if not records:
        return False

    payload = read_json(path, {"schemaVersion": 1, "changes": []})
    existing = payload.get("changes", [])
    seen = {str(item.get("changeId", "")) for item in existing}
    new_items = [item for item in records if item["changeId"] not in seen]
    if not new_items:
        return False

    payload["schemaVersion"] = 1
    payload["updatedAt"] = new_items[-1]["observedAt"]
    payload["changes"] = (new_items + existing)[:max_records]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return True


def load_push_state(path: Path) -> dict:
    return read_json(path, {"schemaVersion": 1, "sent": {}, "pending": {}})


def already_sent_ids(state: dict) -> set[str]:
    return set((state.get("sent") or {}).keys())


def enqueue_pending(path: Path, state: dict, records: list[dict]) -> tuple[dict, bool]:
    sent = dict(state.get("sent") or {})
    pending = dict(state.get("pending") or {})
    changed = False

    for record in records:
        item_id = record["changeId"]
        if item_id in sent or item_id in pending:
            continue
        pending[item_id] = record
        changed = True

    payload = {
        "schemaVersion": 1,
        "updatedAt": datetime.now(OSLO).isoformat(timespec="seconds"),
        "sent": sent,
        "pending": pending,
    }

    if changed:
        path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    return payload, changed


def pending_records(state: dict) -> list[dict]:
    pending = state.get("pending") or {}
    return list(pending.values())


def mark_sent(path: Path, state: dict, change_ids: list[str], sent_at: str | None = None, max_ids: int = 2000) -> bool:
    if not change_ids:
        return False

    sent_at = sent_at or datetime.now(OSLO).isoformat(timespec="seconds")
    sent = dict(state.get("sent") or {})
    pending = dict(state.get("pending") or {})
    changed = False

    for item in change_ids:
        if item not in sent or item in pending:
            changed = True
        sent[item] = sent_at
        pending.pop(item, None)

    ordered = sorted(sent.items(), key=lambda item: item[1], reverse=True)[:max_ids]
    payload = {
        "schemaVersion": 1,
        "updatedAt": sent_at,
        "sent": dict(ordered),
        "pending": pending,
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return changed


def suspicious_snapshot(old_count: int, new_count: int, html_length: int) -> str | None:
    if html_length < 500:
        return "KOVA response was unexpectedly short"
    if old_count >= 5 and new_count == 0:
        return "KOVA returned zero events while a populated snapshot exists"
    if old_count >= 20 and new_count < max(3, int(old_count * 0.25)):
        return f"KOVA event count dropped unexpectedly from {old_count} to {new_count}"
    return None
