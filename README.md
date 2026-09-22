# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.4.0
- KOVA Bridge sjekker offentlige KOVA-kalendere hvert 15. minutt
- Android bruker Bridge først og direkte KOVA som fallback
- Firebase Cloud Messaging-klient er bygget inn
- Appen abonnerer automatisk på valgt hjelpekorps sitt FCM-topic når Firebase er konfigurert
- Bridge kan sende push via FCM HTTP v1 når GitHub-secret er satt
- Lokal 15-minutters synk fungerer fortsatt dersom Firebase ikke er konfigurert
- Push-status vises i appen
- Ullensaker, Eidsvoll/Hurdal, Nittedal og Skedsmo støttes

## Firebase-oppsett

Firebase Android-klientkonfigurasjonen er registrert i appen for package `no.juliannordli.kovacomp`.

Bridge trenger én privat GitHub repository secret:

- `FIREBASE_SERVICE_ACCOUNT_JSON` – hele JSON-innholdet fra Firebase Admin SDK-servicekontoen.

Servicekonto eller privat nøkkel skal aldri legges inn i repositoryet.

Når secretet finnes, kan Bridge sende push via FCM HTTP v1 til topic for valgt korps.

## Topic-format

App og Bridge bruker samme topic-format:

- `kova_ullensakerrkh`
- `kova_ehrkh`
- `kova_nittedal_rkh`
- `kova_skedsmo_rkh`

## Datasikkerhet

KOVA Companion v0.4 bruker fortsatt bare offentlig KOVA-data. Røde Kors-passord, Okta-cookies og private KOVA-data behandles ikke.

## Neste steg

- legge inn `FIREBASE_SERVICE_ACCOUNT_JSON` som GitHub Actions-secret
- sende første ekte push-test med `FCM smoke test`
- senere autentisert KOVA-modul dersom offisiell API/OIDC-tilgang blir tilgjengelig
