from __future__ import annotations

import json
import os

import firebase_admin
import requests
from firebase_admin import auth, credentials, firestore
from google.auth.transport.requests import Request
from google.oauth2 import service_account

PROJECT_ID = "kova-companion"
ADMIN_EMAIL = "superuser@kova-companion.local"
ADMIN_USERNAME = "superuser"


def service_info() -> dict:
    raw = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        raise RuntimeError("FIREBASE_SERVICE_ACCOUNT_JSON is not configured")
    return json.loads(raw)


def enable_email_password(info: dict) -> None:
    creds = service_account.Credentials.from_service_account_info(
        info,
        scopes=["https://www.googleapis.com/auth/cloud-platform"],
    )
    creds.refresh(Request())
    response = requests.patch(
        f"https://identitytoolkit.googleapis.com/admin/v2/projects/{PROJECT_ID}/config",
        params={"updateMask": "signIn.email.enabled,signIn.email.passwordRequired"},
        headers={
            "Authorization": f"Bearer {creds.token}",
            "Content-Type": "application/json",
        },
        json={
            "signIn": {
                "email": {
                    "enabled": True,
                    "passwordRequired": True,
                }
            }
        },
        timeout=30,
    )
    if response.ok:
        print("Firebase Email/Password sign-in enabled.")
    else:
        print(
            "Firebase Auth provider update skipped/failed:",
            response.status_code,
            response.text[:800],
        )


def main() -> None:
    info = service_info()
    enable_email_password(info)

    if not firebase_admin._apps:
        firebase_admin.initialize_app(
            credentials.Certificate(info),
            {"projectId": PROJECT_ID},
        )

    db = firestore.client()

    # Public, non-sensitive control document used by every PWA to detect
    # a remotely requested cache generation change.
    config_ref = db.collection("publicConfig").document("pwa")
    if not config_ref.get().exists:
        config_ref.set({
            "cacheEpoch": 1,
            "updatedAt": firestore.SERVER_TIMESTAMP,
        })
        print("Created PWA public cache control document.")

    try:
        user = auth.get_user_by_email(ADMIN_EMAIL)
        print("Admin user exists in Firebase Authentication.")
    except auth.UserNotFoundError:
        user = None
        print("Admin user does NOT exist in Firebase Authentication.")

    temp_password = os.getenv("KOVA_ADMIN_TEMP_PASSWORD", "").strip()
    if not temp_password and user is None:
        print(
            "KOVA_ADMIN_TEMP_PASSWORD is not configured. "
            "Admin account provisioning is intentionally skipped."
        )
        return

    if user is None:
        user = auth.create_user(
            email=ADMIN_EMAIL,
            password=temp_password,
            display_name=ADMIN_USERNAME,
            disabled=False,
        )
        print("Created KOVA Admin superuser.")

    auth.set_custom_user_claims(
        user.uid,
        {"admin": True, "role": "superuser"},
    )

    profile_ref = db.collection("adminUsers").document(user.uid)
    snapshot = profile_ref.get()
    current = snapshot.to_dict() if snapshot.exists else {}
    profile_ref.set({
        "username": ADMIN_USERNAME,
        "email": ADMIN_EMAIL,
        "role": "superuser",
        "mustChangePassword": bool(
            current.get("mustChangePassword", True)
        ),
        "updatedAt": firestore.SERVER_TIMESTAMP,
        **({} if snapshot.exists else {
            "createdAt": firestore.SERVER_TIMESTAMP,
        }),
    }, merge=True)
    print("KOVA Admin profile is ready with forced first-login password change.")


if __name__ == "__main__":
    main()
