"""Device-scoped push tests, requested using the subscription's private token."""
from datetime import datetime, timezone
import requests
from webpush import (
    _credentials, _list_collection_documents, _list_subscription_documents,
    _document_id, _string_field, _bool_field, _headers, _send_to_subscription,
    ensure_vapid_config,
)


def eligible(request, subscription, now):
    fields = request.get("fields", {})
    stamp = fields.get("requestedAt", {}).get("timestampValue", "")
    try:
        age = (now - datetime.fromisoformat(stamp.replace("Z", "+00:00"))).total_seconds()
    except (ValueError, TypeError):
        return False
    return (
        0 <= age <= 1800
        and stamp != _string_field(request, "processedRequest")
        and _bool_field(subscription, "enabled")
        and bool(_string_field(subscription, "reminderToken"))
        and _string_field(request, "reminderToken") == _string_field(subscription, "reminderToken")
    )


def send_push_selftests(now=None):
    credentials, project = _credentials()
    if credentials is None:
        return 0
    requests_pending = _list_collection_documents(credentials, project, "webPushTests")
    if not requests_pending:
        return 0
    subscriptions = {_document_id(d): d for d in _list_subscription_documents(credentials, project)}
    now = now or datetime.now(timezone.utc)
    pending = [r for r in requests_pending if eligible(r, subscriptions.get(_document_id(r), {}), now)][:10]
    if not pending:
        return 0
    config = ensure_vapid_config()
    sent = 0
    for request in pending:
        stamp = request["fields"]["requestedAt"]["timestampValue"]
        # Claim before delivery to prevent duplicate tests after a process restart.
        response = requests.patch(
            "https://firestore.googleapis.com/v1/" + request["name"],
            headers=_headers(credentials), timeout=30,
            params={"updateMask.fieldPaths": "processedRequest", "currentDocument.updateTime": request["updateTime"]},
            json={"fields": {"processedRequest": {"stringValue": stamp}}},
        )
        if response.status_code in (409, 412):
            continue
        response.raise_for_status()
        _, delivered = _send_to_subscription(subscriptions[_document_id(request)], {
            "title": "Kova Companion – testvarsel",
            "body": "Push virker på denne enheten. Dette er testen du selv bestilte.",
            "kind": "test", "changeId": "selftest-" + stamp,
            "url": "./", "organization": "",
        }, config, credentials)
        sent += int(delivered)
    return sent
