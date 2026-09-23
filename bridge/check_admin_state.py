from __future__ import annotations

import json
import os

import firebase_admin
from firebase_admin import credentials, firestore

PROJECT_ID = "kova-companion"
ADMIN_EMAIL = "superuser@kova-companion.local"

raw = os.environ["FIREBASE_SERVICE_ACCOUNT_JSON"]
info = json.loads(raw)

if not firebase_admin._apps:
    firebase_admin.initialize_app(
        credentials.Certificate(info),
        {"projectId": PROJECT_ID},
    )

db = firestore.client()
docs = list(db.collection("adminUsers").stream())
matches = [d.to_dict() for d in docs if (d.to_dict() or {}).get("email") == ADMIN_EMAIL]

if len(matches) != 1:
    raise SystemExit(f"Expected one superuser profile, found {len(matches)}")

profile = matches[0]
print("ADMIN_PROFILE_FOUND=true")
print("ADMIN_ROLE=" + str(profile.get("role", "")))
print("ADMIN_MUST_CHANGE_PASSWORD=" + str(bool(profile.get("mustChangePassword", True))).lower())
if profile.get("mustChangePassword", True):
    raise SystemExit("Password change flag is still true")
