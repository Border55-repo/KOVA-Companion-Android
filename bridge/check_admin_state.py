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


cache_doc = db.collection("publicConfig").document("pwa").get()
if not cache_doc.exists:
    raise SystemExit("PWA cache control document missing")
cache = cache_doc.to_dict() or {}
epoch = int(cache.get("cacheEpoch", 0))
print("PWA_CACHE_EPOCH=" + str(epoch))
if epoch < 2:
    raise SystemExit("PWA cache generation was not incremented")


runtime_doc = db.collection("adminRuntime").document("bridge").get()
if not runtime_doc.exists:
    raise SystemExit("Admin runtime document missing")
runtime = runtime_doc.to_dict() or {}
print("ADMIN_RUNTIME_STATUS=" + str(runtime.get("status", "")))
print("ADMIN_RUNTIME_LAST_RUN=" + str(runtime.get("lastRunAt", "")))
print("ADMIN_RUNTIME_POLLED=" + str(runtime.get("polledThisRun", "")))
print("ADMIN_RUNTIME_FAILURES=" + str(runtime.get("failures", "")))
checks = runtime.get("organizationChecks") or {}
print("ADMIN_RUNTIME_ORG_CHECKS=" + str(len(checks)))
if runtime.get("status") not in ("ok", "degraded", "error"):
    raise SystemExit("Unexpected admin runtime status")
if not runtime.get("lastRunAt"):
    raise SystemExit("Admin runtime lastRunAt missing")


command_doc = db.collection("adminCommands").document("bridgeSync").get()
if not command_doc.exists:
    raise SystemExit("Admin Bridge sync command document missing")
command = command_doc.to_dict() or {}
print("ADMIN_SYNC_COMMAND_STATUS=" + str(command.get("status", "")))
print("ADMIN_SYNC_COMMAND_POLLED=" + str(command.get("polled", "")))
print("ADMIN_SYNC_COMMAND_FAILURES=" + str(command.get("failures", "")))
if command.get("status") != "completed":
    raise SystemExit("Latest admin Bridge sync command is not completed")
if int(command.get("polled", 0) or 0) < 42:
    raise SystemExit("Admin full sync did not poll all organizations")
if int(command.get("failures", 0) or 0) != 0:
    raise SystemExit("Admin full sync completed with failures")

if len(checks) < 38:
    raise SystemExit(
        f"Expected runtime checks for at least 38 hjelpekorps, found {len(checks)}"
    )
