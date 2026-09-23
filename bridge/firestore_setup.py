from __future__ import annotations

import json
import os
import time

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

BASE = "https://firestore.googleapis.com/v1"
LOCATION = os.getenv("FIRESTORE_LOCATION", "eur3")
PROJECT_NUMBER = os.getenv("FIREBASE_PROJECT_NUMBER", "1007303072111")
SERVICE_USAGE = "https://serviceusage.googleapis.com/v1"


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


def wait_operation(name: str, creds, base: str = BASE, label: str = "operation"):
    url = f"{base}/{name}"
    for _ in range(60):
        response = requests.get(url, headers=headers(creds), timeout=30)
        response.raise_for_status()
        payload = response.json()
        if payload.get("done"):
            if payload.get("error"):
                raise RuntimeError(f"{label} failed: {payload['error']}")
            return
        time.sleep(2)
    raise TimeoutError(f"Timed out waiting for {label}")


def enable_firestore_api(creds):
    url = (
        f"{SERVICE_USAGE}/projects/{PROJECT_NUMBER}/services/"
        "firestore.googleapis.com:enable"
    )
    response = requests.post(url, headers=headers(creds), json={}, timeout=30)
    if not response.ok:
        print("Firestore API enable failed:", response.status_code, response.text[:1200], flush=True)
    response.raise_for_status()
    operation = response.json()
    wait_operation(operation["name"], creds, SERVICE_USAGE, "Firestore API enablement")
    print("Enabled firestore.googleapis.com.")



def main():
    creds, project_id = credentials()
    database_url = f"{BASE}/projects/{project_id}/databases/(default)"
    response = requests.get(database_url, headers=headers(creds), timeout=30)

    if response.status_code == 403 and "SERVICE_DISABLED" in response.text:
        print("Firestore API is disabled; enabling it now.", flush=True)
        enable_firestore_api(creds)
        time.sleep(3)
        response = requests.get(database_url, headers=headers(creds), timeout=30)

    if response.status_code == 200:
        payload = response.json()
        print(f"Firestore database already exists in {payload.get('locationId', 'unknown location')}.")
        return

    if response.status_code != 404:
        print("Firestore metadata check failed:", response.status_code, response.text[:1200], flush=True)
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
    if not create.ok:
        print("Firestore create failed:", create.status_code, create.text[:1200], flush=True)
    create.raise_for_status()
    operation = create.json()
    wait_operation(operation["name"], creds)
    print(f"Created Firestore (default) in {LOCATION} with delete protection.")


if __name__ == "__main__":
    main()
