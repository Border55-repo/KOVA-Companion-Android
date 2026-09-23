from __future__ import annotations

import json
import os
from pathlib import Path

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

RULES_API = "https://firebaserules.googleapis.com/v1"
RULES_FILE = Path(__file__).resolve().parents[1] / "firestore.rules"


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


def main():
    creds, project_id = credentials()
    content = RULES_FILE.read_text(encoding="utf-8")

    ruleset_response = requests.post(
        f"{RULES_API}/projects/{project_id}/rulesets",
        headers=headers(creds),
        json={
            "source": {
                "files": [
                    {
                        "name": "firestore.rules",
                        "content": content,
                    }
                ]
            }
        },
        timeout=30,
    )
    if not ruleset_response.ok:
        print("Create ruleset failed:", ruleset_response.status_code, ruleset_response.text[:2000], flush=True)
    ruleset_response.raise_for_status()
    ruleset_name = ruleset_response.json()["name"]

    release_name = f"projects/{project_id}/releases/cloud.firestore"
    get_release = requests.get(
        f"{RULES_API}/{release_name}",
        headers=headers(creds),
        timeout=30,
    )

    payload = {"rulesetName": ruleset_name}
    if get_release.status_code == 404:
        release_response = requests.post(
            f"{RULES_API}/projects/{project_id}/releases",
            params={"releaseId": "cloud.firestore"},
            headers=headers(creds),
            json={
                "name": release_name,
                "rulesetName": ruleset_name,
            },
            timeout=30,
        )
    else:
        get_release.raise_for_status()
        release_response = requests.patch(
            f"{RULES_API}/{release_name}",
            headers=headers(creds),
            json=payload,
            timeout=30,
        )

    if not release_response.ok:
        print("Release ruleset failed:", release_response.status_code, release_response.text[:2000], flush=True)
    release_response.raise_for_status()
    print(f"Firestore rules released via Firebase Rules API: {ruleset_name}")


if __name__ == "__main__":
    main()
