from __future__ import annotations

import json
import os
import time

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

BASE = "https://firestore.googleapis.com/v1"
LOCATION = os.getenv("FIRESTORE_LOCATION", "eur3")


def credentials():
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON is not configured")
    info = json.loads(raw)
    creds = service_account.Credentials.from_service_account_info(
        info,
        scopes=["https://www.googleapis.com/auth/cloud-platform"],
    )
    creds.refresh(Request())
    return creds, info["project_id"]


def headers(creds):
    return {
        "Authorization": f"Bearer {creds.token}",
        "Content-Type": "application/json",
    }


def wait_operation(name: str, creds):
    url = f"{BASE}/{name}"
    for _ in range(60):
        response = requests.get(url, headers=headers(creds), timeout=30)
        response.raise_for_status()
        payload = response.json()
        if payload.get("done"):
            if payload.get("error"):
                raise RuntimeError(f"Firestore operation failed: {payload['error']}")
            return
        time.sleep(2)
    raise TimeoutError("Timed out waiting for Firestore database creation")


def main():
    creds, project_id = credentials()
    database_url = f"{BASE}/projects/{project_id}/databases/(default)"
    response = requests.get(database_url, headers=headers(creds), timeout=30)

    if response.status_code == 200:
        payload = response.json()
        print(f"Firestore database already exists in {payload.get('locationId', 'unknown location')}.")
        return

    if response.status_code != 404:
        response.raise_for_status()

    create = requests.post(
        f"{BASE}/projects/{project_id}/databases",
        params={"databaseId": "(default)"},
        headers=headers(creds),
        json={
            "locationId": LOCATION,
            "type": "FIRESTORE_NATIVE",
            "deleteProtectionState": "DELETE_PROTECTION_ENABLED",
        },
        timeout=30,
    )
    create.raise_for_status()
    operation = create.json()
    wait_operation(operation["name"], creds)
    print(f"Created Firestore (default) in {LOCATION} with delete protection.")


if __name__ == "__main__":
    main()
