from __future__ import annotations

import json
import os
import uuid

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

PROJECT_ID = "kova-companion"
BASE = f"https://firestore.googleapis.com/v1/projects/{PROJECT_ID}/databases/(default)"
DOC_ID = "ci-" + uuid.uuid4().hex[:20]
DOC_NAME = f"projects/{PROJECT_ID}/databases/(default)/documents/webPushSubscriptions/{DOC_ID}"
DOC_URL = f"https://firestore.googleapis.com/v1/{DOC_NAME}"
LEGACY_DOC_NAME = DOC_NAME + "-legacy"
LEGACY_DOC_URL = f"https://firestore.googleapis.com/v1/{LEGACY_DOC_NAME}"
REMINDER_TOKEN = "t" * 64


def admin_credentials():
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON is not configured")
    info = json.loads(raw)
    creds = service_account.Credentials.from_service_account_info(
        info,
        scopes=["https://www.googleapis.com/auth/datastore"],
    )
    creds.refresh(Request())
    return creds


def valid_write_body():
    return {
        "writes": [
            {
                "update": {
                    "name": DOC_NAME,
                    "fields": {
                        "endpoint": {
                            "stringValue": "https://push.example.invalid/subscription/" + ("x" * 64)
                        },
                        "p256dh": {"stringValue": "B" + ("a" * 86)},
                        "auth": {"stringValue": "a" * 22},
                        "organizations": {
                            "arrayValue": {
                                "values": [{"stringValue": "UllensakerRKH"}]
                            }
                        },
                        "enabled": {"booleanValue": False},
                        "platform": {"stringValue": "pwa"},
                        "reminderToken": {"stringValue": REMINDER_TOKEN},
                    },
                },
                "updateTransforms": [
                    {
                        "fieldPath": "updatedAt",
                        "setToServerValue": "REQUEST_TIME",
                    }
                ],
            }
        ]
    }


def run_checks(creds):
    # 1) Valid anonymous client-like write must be allowed by rules.
    response = requests.post(
        f"{BASE}/documents:commit",
        headers={"Content-Type": "application/json"},
        json=valid_write_body(),
        timeout=30,
    )
    if not response.ok:
        raise RuntimeError(
            f"Valid anonymous subscription write was rejected: "
            f"{response.status_code} {response.text[:1200]}"
        )
    print("Valid anonymous Web Push subscription write allowed.")

    # 2) Anonymous read must remain blocked.
    read_response = requests.get(DOC_URL, timeout=30)
    if read_response.status_code not in (401, 403):
        raise RuntimeError(
            f"Anonymous subscription read was not blocked: "
            f"{read_response.status_code} {read_response.text[:500]}"
        )
    print("Anonymous Web Push subscription read blocked.")

    # 3) Invalid write must be rejected.
    invalid = valid_write_body()
    invalid["writes"][0]["update"]["fields"]["platform"]["stringValue"] = "not-pwa"
    invalid["writes"][0]["update"]["name"] = DOC_NAME + "-invalid"
    invalid_response = requests.post(
        f"{BASE}/documents:commit",
        headers={"Content-Type": "application/json"},
        json=invalid,
        timeout=30,
    )
    if invalid_response.status_code not in (401, 403):
        raise RuntimeError(
            f"Invalid anonymous write was not blocked: "
            f"{invalid_response.status_code} {invalid_response.text[:500]}"
        )
    print("Invalid anonymous subscription write blocked.")

    # 4) Newly created subscriptions must carry a private recovery token.
    missing_token = valid_write_body()
    missing_token["writes"][0]["update"]["fields"].pop("reminderToken")
    missing_token["writes"][0]["update"]["name"] = DOC_NAME + "-no-token"
    missing_token_response = requests.post(
        f"{BASE}/documents:commit",
        headers={"Content-Type": "application/json"},
        json=missing_token,
        timeout=30,
    )
    if missing_token_response.status_code not in (401, 403):
        raise RuntimeError(
            f"Anonymous subscription create without token was not blocked: "
            f"{missing_token_response.status_code} {missing_token_response.text[:500]}"
        )
    print("Subscription create without recovery token blocked.")

    # 5) Knowing a document ID must not permit replacing its push keys and token.
    takeover = valid_write_body()
    takeover["writes"][0]["update"]["fields"]["auth"]["stringValue"] = "b" * 22
    takeover["writes"][0]["update"]["fields"]["reminderToken"]["stringValue"] = "u" * 64
    takeover_response = requests.post(
        f"{BASE}/documents:commit",
        headers={"Content-Type": "application/json"},
        json=takeover,
        timeout=30,
    )
    if takeover_response.status_code not in (401, 403):
        raise RuntimeError(
            f"Anonymous subscription takeover was not blocked: "
            f"{takeover_response.status_code} {takeover_response.text[:500]}"
        )
    print("Subscription takeover with different keys and token blocked.")

    # 6) The same browser subscription can recover after losing local storage.
    recovery = valid_write_body()
    recovery["writes"][0]["update"]["fields"]["reminderToken"]["stringValue"] = "v" * 64
    recovery_response = requests.post(
        f"{BASE}/documents:commit",
        headers={"Content-Type": "application/json"},
        json=recovery,
        timeout=30,
    )
    if not recovery_response.ok:
        raise RuntimeError(
            f"Subscription recovery with unchanged push keys was rejected: "
            f"{recovery_response.status_code} {recovery_response.text[:500]}"
        )
    print("Subscription token recovery with unchanged push keys allowed.")

    # 7) An older subscription without a token can claim one using its original keys.
    legacy = valid_write_body()
    legacy["writes"][0]["update"]["name"] = LEGACY_DOC_NAME
    legacy["writes"][0]["update"]["fields"].pop("reminderToken")
    legacy_create = requests.post(
        f"{BASE}/documents:commit",
        headers={"Authorization": f"Bearer {creds.token}"},
        json=legacy,
        timeout=30,
    )
    if not legacy_create.ok:
        raise RuntimeError(
            f"Could not create legacy subscription probe: "
            f"{legacy_create.status_code} {legacy_create.text[:500]}"
        )
    legacy_claim = valid_write_body()
    legacy_claim["writes"][0]["update"]["name"] = LEGACY_DOC_NAME
    legacy_claim_response = requests.post(
        f"{BASE}/documents:commit",
        headers={"Content-Type": "application/json"},
        json=legacy_claim,
        timeout=30,
    )
    if not legacy_claim_response.ok:
        raise RuntimeError(
            f"Legacy subscription token claim was rejected: "
            f"{legacy_claim_response.status_code} {legacy_claim_response.text[:500]}"
        )
    print("Legacy subscription token claim with unchanged push keys allowed.")

    # 8) Public PWA cache generation must be readable without auth.
    public_config_url = (
        "https://firestore.googleapis.com/v1/"
        f"projects/{PROJECT_ID}/databases/(default)/documents/publicConfig/pwa"
    )
    public_read = requests.get(public_config_url, timeout=30)
    if not public_read.ok:
        raise RuntimeError(
            f"Public PWA cache config was not readable: "
            f"{public_read.status_code} {public_read.text[:500]}"
        )
    print("Public PWA cache config read allowed.")

    # 9) Anonymous clients must not be able to change cache generation.
    public_doc = public_read.json()
    current_epoch = int(
        ((public_doc.get("fields") or {}).get("cacheEpoch") or {}).get("integerValue", "1")
    )
    public_write = requests.patch(
        public_config_url,
        params={"updateMask.fieldPaths": "cacheEpoch"},
        headers={"Content-Type": "application/json"},
        json={"fields": {"cacheEpoch": {"integerValue": str(current_epoch + 1)}}},
        timeout=30,
    )
    if public_write.status_code not in (401, 403):
        raise RuntimeError(
            f"Anonymous cache control write was not blocked: "
            f"{public_write.status_code} {public_write.text[:500]}"
        )
    print("Anonymous PWA cache control write blocked.")

    # 10) Admin profile collection must not be publicly readable.
    admin_probe = requests.get(
        f"{BASE}/documents/adminUsers",
        timeout=30,
    )
    if admin_probe.status_code not in (401, 403, 404):
        raise RuntimeError(
            f"Anonymous admin profile read was not blocked: "
            f"{admin_probe.status_code} {admin_probe.text[:500]}"
        )
    print("Anonymous admin profile read blocked.")

    # 11) Live admin runtime must remain private.
    runtime_probe = requests.get(
        f"{BASE}/documents/adminRuntime/bridge",
        timeout=30,
    )
    if runtime_probe.status_code not in (401, 403, 404):
        raise RuntimeError(
            f"Anonymous admin runtime read was not blocked: "
            f"{runtime_probe.status_code} {runtime_probe.text[:500]}"
        )
    print("Anonymous admin runtime read blocked.")

    # 12) Bridge sync commands must remain private and unwritable anonymously.
    command_url = f"{BASE}/documents/adminCommands/bridgeSync"
    command_read = requests.get(command_url, timeout=30)
    if command_read.status_code not in (401, 403, 404):
        raise RuntimeError(
            f"Anonymous admin command read was not blocked: "
            f"{command_read.status_code} {command_read.text[:500]}"
        )
    print("Anonymous admin command read blocked.")

    command_write = requests.patch(
        command_url,
        headers={"Content-Type": "application/json"},
        json={
            "fields": {
                "action": {"stringValue": "bridgeSync"},
                "status": {"stringValue": "requested"},
                "requestId": {"stringValue": "anonymous-test-request"},
                "requestedBy": {"stringValue": "superuser"},
                "requestedAt": {"timestampValue": "2026-09-23T15:00:00Z"},
            }
        },
        timeout=30,
    )
    if command_write.status_code not in (401, 403):
        raise RuntimeError(
            f"Anonymous admin command write was not blocked: "
            f"{command_write.status_code} {command_write.text[:500]}"
        )
    print("Anonymous admin command write blocked.")

def main():
    creds = admin_credentials()
    try:
        run_checks(creds)
    finally:
        # Server credentials bypass client rules via IAM. Clean up even if a check fails.
        for doc_url in (DOC_URL, LEGACY_DOC_URL):
            delete_response = requests.delete(
                doc_url,
                headers={"Authorization": f"Bearer {creds.token}"},
                timeout=30,
            )
            if delete_response.status_code not in (200, 404):
                raise RuntimeError(
                    f"Could not clean up Web Push rules probe: "
                    f"{delete_response.status_code} {delete_response.text[:500]}"
                )
        print("Web Push rules probe cleaned up.")


if __name__ == "__main__":
    main()
