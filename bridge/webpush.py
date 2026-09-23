from __future__ import annotations

import base64
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import requests
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from google.auth.transport.requests import Request
from google.oauth2 import service_account
from pywebpush import WebPushException, webpush

FIRESTORE_SCOPE = "https://www.googleapis.com/auth/datastore"
PUBLIC_CONFIG_PATH = Path(__file__).resolve().parent / "data" / "webpush-config.json"
VAPID_DOC = "webPushConfig/vapid"
SUBSCRIPTIONS_COLLECTION = "webPushSubscriptions"


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _credentials():
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        return None, None
    info = json.loads(raw)
    credentials = service_account.Credentials.from_service_account_info(
        info,
        scopes=[FIRESTORE_SCOPE],
    )
    credentials.refresh(Request())
    return credentials, info["project_id"]


def _headers(credentials) -> dict[str, str]:
    return {
        "Authorization": f"Bearer {credentials.token}",
        "Content-Type": "application/json",
    }


def _doc_url(project_id: str, path: str) -> str:
    return (
        "https://firestore.googleapis.com/v1/"
        f"projects/{project_id}/databases/(default)/documents/{path}"
    )


def _string_field(document: dict, key: str) -> str:
    return str(((document.get("fields") or {}).get(key) or {}).get("stringValue", ""))


def _bool_field(document: dict, key: str, default: bool = False) -> bool:
    value = ((document.get("fields") or {}).get(key) or {}).get("booleanValue")
    return default if value is None else bool(value)


def _array_strings(document: dict, key: str) -> list[str]:
    values = (((document.get("fields") or {}).get(key) or {}).get("arrayValue") or {}).get("values", [])
    return [str(item.get("stringValue", "")) for item in values if item.get("stringValue")]


def ensure_vapid_config() -> dict[str, str]:
    credentials, project_id = _credentials()
    if credentials is None:
        raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON is required for Web Push setup")

    url = _doc_url(project_id, VAPID_DOC)
    response = requests.get(url, headers=_headers(credentials), timeout=30)

    if response.status_code == 404:
        private_key = ec.generate_private_key(ec.SECP256R1())
        private_der = private_key.private_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        )
        public_raw = private_key.public_key().public_bytes(
            encoding=serialization.Encoding.X962,
            format=serialization.PublicFormat.UncompressedPoint,
        )
        public_key = _b64url(public_raw)
        private_key_encoded = _b64url(private_der)

        payload = {
            "fields": {
                "publicKey": {"stringValue": public_key},
                "privateKey": {"stringValue": private_key_encoded},
                "createdAt": {"timestampValue": datetime.now(timezone.utc).isoformat()},
            }
        }
        response = requests.patch(url, headers=_headers(credentials), json=payload, timeout=30)
        response.raise_for_status()
        document = response.json()
        print("Created persistent VAPID key pair in server-only Firestore config.")
    else:
        response.raise_for_status()
        document = response.json()
        public_key = _string_field(document, "publicKey")
        private_key_encoded = _string_field(document, "privateKey")
        if not public_key or not private_key_encoded:
            raise RuntimeError("Stored VAPID configuration is incomplete")
        print("Persistent VAPID key pair already exists.")

    public_payload = {
        "schemaVersion": 1,
        "publicKey": public_key,
    }
    PUBLIC_CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    PUBLIC_CONFIG_PATH.write_text(
        json.dumps(public_payload, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return {
        "publicKey": public_key,
        "privateKey": private_key_encoded,
        "projectId": project_id,
    }


def _list_subscription_documents(credentials, project_id: str) -> list[dict]:
    url = _doc_url(project_id, SUBSCRIPTIONS_COLLECTION)
    documents: list[dict] = []
    token = None

    while True:
        params: dict[str, Any] = {"pageSize": 500}
        if token:
            params["pageToken"] = token
        response = requests.get(url, headers=_headers(credentials), params=params, timeout=30)
        if response.status_code == 404:
            return []
        response.raise_for_status()
        payload = response.json()
        documents.extend(payload.get("documents", []))
        token = payload.get("nextPageToken")
        if not token:
            return documents


def _disable_subscription(credentials, document_name: str) -> None:
    url = f"https://firestore.googleapis.com/v1/{document_name}"
    response = requests.patch(
        url,
        headers=_headers(credentials),
        params={"updateMask.fieldPaths": "enabled"},
        json={"fields": {"enabled": {"booleanValue": False}}},
        timeout=30,
    )
    if not response.ok:
        print(f"Could not disable stale Web Push subscription: {response.status_code}")


def send_web_notification(
    organization: str,
    title: str,
    body: str,
    kind: str,
    event: dict | None,
    change_id_value: str,
) -> bool:
    credentials, project_id = _credentials()
    if credentials is None:
        print("Web Push disabled: Firebase service account is not configured.")
        return True

    config = ensure_vapid_config()
    documents = _list_subscription_documents(credentials, project_id)
    targets = [
        document
        for document in documents
        if _bool_field(document, "enabled", True)
        and organization in _array_strings(document, "organizations")
    ]

    print(
        f"Web Push subscriptions: total={len(documents)}, "
        f"matching={len(targets)} for {organization}."
    )
    if not targets:
        return True

    payload = json.dumps(
        {
            "title": title,
            "body": body,
            "kind": kind,
            "organization": organization,
            "changeId": change_id_value,
            "event": event or {},
        },
        ensure_ascii=False,
    )

    transient_failure = False
    sent = 0
    for document in targets:
        subscription = {
            "endpoint": _string_field(document, "endpoint"),
            "keys": {
                "p256dh": _string_field(document, "p256dh"),
                "auth": _string_field(document, "auth"),
            },
        }
        if not all([subscription["endpoint"], subscription["keys"]["p256dh"], subscription["keys"]["auth"]]):
            continue
        try:
            webpush(
                subscription_info=subscription,
                data=payload,
                vapid_private_key=config["privateKey"],
                vapid_claims={"sub": "mailto:kova-companion@users.noreply.github.com"},
                ttl=3600,
                timeout=20,
            )
            sent += 1
        except WebPushException as exc:
            status = exc.status_code
            if status in (404, 410):
                _disable_subscription(credentials, document["name"])
                print(f"Disabled stale Web Push subscription ({status}).")
            else:
                transient_failure = True
                print(f"Web Push failed ({status or 'unknown'}): {exc}", flush=True)

    print(f"Web Push sent to {sent}/{len(targets)} subscription(s) for {organization}.")
    return not transient_failure
