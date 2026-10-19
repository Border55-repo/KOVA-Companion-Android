"""Credentials for GitHub Bridge and the managed Firebase Functions runtime."""
from __future__ import annotations

import json
import os

import google.auth
from google.auth.transport.requests import Request
from google.oauth2 import service_account


def credentials_for(scopes):
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if raw:
        info = json.loads(raw)
        project_id = info.get("project_id")
        if not project_id:
            raise RuntimeError("Firebase service account is missing project_id")
        credentials = service_account.Credentials.from_service_account_info(info, scopes=scopes)
    elif os.getenv("K_SERVICE"):
        # Cloud Functions uses its assigned runtime identity, never a private key
        # copied into code, the browser, or a function environment variable.
        credentials, project_id = google.auth.default(scopes=scopes)
        if not project_id:
            raise RuntimeError("Managed runtime project is unavailable")
    else:
        return None, None
    credentials.refresh(Request())
    return credentials, project_id
