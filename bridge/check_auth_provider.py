import json, os, requests

raw=os.environ["FIREBASE_WEB_CONFIG_JSON"]
config=json.loads(raw)
api_key=config["apiKey"]

url=f"https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={api_key}"
payload={
    "email":"superuser@kova-companion.local",
    "password":"definitely-not-the-real-password",
    "returnSecureToken":True,
}
r=requests.post(url,json=payload,timeout=30)
print("status",r.status_code)
data=r.json()
message=((data.get("error") or {}).get("message") or "")
if message=="OPERATION_NOT_ALLOWED":
    print("EMAIL_PASSWORD_PROVIDER=DISABLED")
elif message in ("INVALID_LOGIN_CREDENTIALS","INVALID_PASSWORD","EMAIL_NOT_FOUND"):
    print("EMAIL_PASSWORD_PROVIDER=ENABLED")
else:
    print("EMAIL_PASSWORD_PROVIDER=UNKNOWN")
    print(message[:200])
