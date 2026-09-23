import json
import requests

with open("bridge/data/firebase-web-config.json", "r", encoding="utf-8") as fh:
    config = json.load(fh)

api_key = config["apiKey"]
url = f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}"
payload = {
    "email": "superuser@kova-companion.local",
    "password": "definitely-not-the-real-password",
    "returnSecureToken": True,
}

r = requests.post(url, json=payload, timeout=30)
print("status", r.status_code)
try:
    data = r.json()
except Exception:
    print("EMAIL_PASSWORD_PROVIDER=UNKNOWN")
    print("NON_JSON_RESPONSE")
    raise SystemExit(1)

message = ((data.get("error") or {}).get("message") or "")
if message == "OPERATION_NOT_ALLOWED":
    print("EMAIL_PASSWORD_PROVIDER=DISABLED")
    raise SystemExit(2)
elif message in ("INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND"):
    print("EMAIL_PASSWORD_PROVIDER=ENABLED")
else:
    print("EMAIL_PASSWORD_PROVIDER=UNKNOWN")
    print(message[:200])
    raise SystemExit(3)
