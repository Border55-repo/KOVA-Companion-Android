from __future__ import annotations

import json
import os
import time
from pathlib import Path

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account

API = "https://firebase.googleapis.com/v1beta1"
DISPLAY_NAME = "KOVA Companion PWA"
OUT = Path(__file__).resolve().parent / "data" / "firebase-web-config.json"


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


def request_json(method, url, creds, **kwargs):
    response = requests.request(method, url, headers=headers(creds), timeout=30, **kwargs)
    response.raise_for_status()
    return response.json()


def wait_operation(operation_name: str, creds):
    url = f"{API}/{operation_name}"
    for _ in range(30):
        payload = request_json("GET", url, creds)
        if payload.get("done"):
            if payload.get("error"):
                raise RuntimeError(f"Firebase operation failed: {payload['error']}")
            return payload.get("response") or {}
        time.sleep(2)
    raise TimeoutError("Timed out waiting for Firebase Web App provisioning")


def main():
    creds, project_id = credentials()
    list_url = f"{API}/projects/{project_id}/webApps"
    apps = request_json("GET", list_url, creds).get("apps", [])
    app = next(
        (
            item
            for item in apps
            if item.get("displayName") == DISPLAY_NAME
            and item.get("state", "ACTIVE") != "DELETED"
        ),
        None,
    )

    if app is None:
        operation = request_json(
            "POST",
            list_url,
            creds,
            json={"displayName": DISPLAY_NAME},
        )
        app = wait_operation(operation["name"], creds)
        print("Firebase Web App created.")
    else:
        print("Firebase Web App already exists.")

    app_name = app.get("name")
    if not app_name:
        # Refresh list in case the create-operation response shape changes.
        apps = request_json("GET", list_url, creds).get("apps", [])
        app = next(item for item in apps if item.get("displayName") == DISPLAY_NAME)
        app_name = app["name"]

    config = request_json("GET", f"{API}/{app_name}/config", creds)
    allowed = [
        "apiKey",
        "authDomain",
        "projectId",
        "storageBucket",
        "messagingSenderId",
        "appId",
        "measurementId",
    ]
    public_config = {key: config[key] for key in allowed if config.get(key)}
    public_config["schemaVersion"] = 1
    public_config["displayName"] = DISPLAY_NAME

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(
        json.dumps(public_config, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote public Firebase Web config for {public_config.get('projectId', project_id)}.")


if __name__ == "__main__":
    main()
