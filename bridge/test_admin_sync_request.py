from __future__ import annotations

import json
import os
import uuid

import firebase_admin
from firebase_admin import credentials, firestore

raw = os.environ["FIREBASE_SERVICE_ACCOUNT_JSON"]
info = json.loads(raw)

if not firebase_admin._apps:
    firebase_admin.initialize_app(
        credentials.Certificate(info),
        {"projectId": "kova-companion"},
    )

db = firestore.client()
request_id = "admin-e2e-" + uuid.uuid4().hex[:12]
db.collection("adminCommands").document("bridgeSync").set({
    "action": "bridgeSync",
    "status": "requested",
    "requestId": request_id,
    "requestedBy": "superuser",
    "requestedAt": firestore.SERVER_TIMESTAMP,
})
print("ADMIN_SYNC_TEST_REQUESTED=true")
print("ADMIN_SYNC_TEST_ID=" + request_id)
