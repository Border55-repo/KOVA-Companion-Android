"""Send activity changes to enabled PWA subscriptions."""
from __future__ import annotations

from reliability import change_id
from webpush import send_web_notification


def change_summary(old: dict, new: dict) -> str:
    labels = [("dateIso", "Dato", "dateLabel"), ("time", "Tid", "time"),
              ("type", "Type", "type"), ("description", "Beskrivelse", "description")]
    changes = [f"{label}: {old.get(display) or old.get(key) or 'ikke oppgitt'} → {new.get(display) or new.get(key) or 'ikke oppgitt'}"
               for key, label, display in labels if old.get(key) != new.get(key)]
    return f"{new.get('description') or 'KOVA-aktivitet'} • " + " • ".join(changes)


def send_diff_notification(org: dict, diff: dict, source_url: str) -> list[str]:
    messages: list[dict] = []
    for event in diff["added"]:
        messages.append({"title": "Ny KOVA-aktivitet",
                         "body": f"{event['description']} • {event['dateLabel']} {event['time']}",
                         "kind": "added", "event": event,
                         "changeId": change_id(org["code"], "added", event)})
    for item in diff["changed"]:
        old, new = item["old"], item["new"]
        messages.append({"title": "KOVA-aktivitet endret", "body": change_summary(old, new),
                         "kind": "changed", "event": new,
                         "changeId": change_id(org["code"], "changed", new, old)})
    for event in diff["removed"]:
        messages.append({"title": "KOVA-aktivitet fjernet",
                         "body": f"{event['description']} • {event['dateLabel']} {event['time']}",
                         "kind": "removed", "event": event,
                         "changeId": change_id(org["code"], "removed", event)})

    sent_ids: list[str] = []
    for message in messages[:10]:
        try:
            accepted = send_web_notification(
                org["code"], message["title"], message["body"], message["kind"],
                message["event"], message["changeId"],
            )
        except Exception:
            accepted = False
            print(f"Web Push failed for {message['changeId']}; keeping pending.", flush=True)
        if accepted:
            sent_ids.append(message["changeId"])
    return sent_ids

