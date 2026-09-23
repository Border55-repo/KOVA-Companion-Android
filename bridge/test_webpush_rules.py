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
                        "enabled": {"booleanValue": True},
                        "platform": {"stringValue": "pwa"},
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


def main():
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

    # Cleanup with service account; server credentials bypass client rules via IAM.
    creds = admin_credentials()
    delete_response = requests.delete(
        DOC_URL,
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
