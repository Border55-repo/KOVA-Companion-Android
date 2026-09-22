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

Android-buildet støtter valgfri GitHub-secret:

- `GOOGLE_SERVICES_JSON` – innholdet fra Firebase sin `google-services.json`

Bridge støtter:

- `FIREBASE_SERVICE_ACCOUNT_JSON` – JSON fra en Firebase/Google Cloud service account med tilgang til Firebase Cloud Messaging

Ingen servicekonto eller privat nøkkel skal legges inn i repositoryet.

Når begge secrets er lagt inn, bygger GitHub Actions en FCM-aktivert APK og Bridge kan sende push til topic for valgt korps.

## Topic-format

App og Bridge bruker samme topic-format:

- `kova_ullensakerrkh`
- `kova_ehrkh`
- `kova_nittedal_rkh`
- `kova_skedsmo_rkh`

## Datasikkerhet

KOVA Companion v0.4 bruker fortsatt bare offentlig KOVA-data. Røde Kors-passord, Okta-cookies og private KOVA-data behandles ikke.

## Neste steg

- aktivere Firebase-prosjektet med app-id `no.juliannordli.kovacomp`
- legge inn GitHub-secrets
- sende første ekte push-test
- senere autentisert KOVA-modul dersom offisiell API/OIDC-tilgang blir tilgjengelig
