# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.6.0
- automatisk sjekk mot siste GitHub Release
- banner i appen når en nyere versjon finnes
- daglig bakgrunnssjekk for nye appversjoner
- oppdateringsvarsel som åpner APK/release direkte
- release-workflow for signert APK + AAB
- automatisk SHA-256-fil for release-artifacts
- tag og Android-versjon må samsvare før release kan publiseres
- Bridge poller offentlig KOVA hvert 5. minutt
- trykk på push åpner riktig aktivitet i appen
- detaljside, aktivitetstypefiltre og Bridge health beholdes
- lokal WorkManager-synk hvert 15. minutt beholdes som fallback

## KOVA-varslingsflyt

1. KOVA publiserer arrangementet på offentlig kalender.
2. Bridge kontrollerer kalenderen hvert 5. minutt.
3. Ved ny, endret eller fjernet aktivitet beregnes diff.
4. Bridge sender data-only FCM via HTTP v1.
5. Android respekterer brukerens varslingsfiltre.
6. Trykk på varselet åpner riktig aktivitetsdetalj.

KOVA kan ha en kort publiseringsforsinkelse mellom lagring i KOVA og synlighet i offentlig kalender. Bridge kan først varsle når aktiviteten faktisk er synlig offentlig.

## Firebase

Firebase Android-klientkonfigurasjonen er registrert for:

- no.juliannordli.kovacomp

Bridge bruker privat GitHub repository secret:

- FIREBASE_SERVICE_ACCOUNT_JSON

Servicekonto eller privat nøkkel skal aldri legges inn i repositoryet.

## Release-signering

Release-workflowen forventer disse GitHub Actions-secrets:

- ANDROID_KEYSTORE_BASE64
- ANDROID_KEYSTORE_PASSWORD
- ANDROID_KEY_ALIAS
- ANDROID_KEY_PASSWORD

Private signing keys skal aldri committes til repositoryet.

Ved tag som v0.6.0 bygger workflowen:

- signert APK
- Android App Bundle (AAB)
- SHA256SUMS.txt
- GitHub Release med genererte release notes

## Oppdateringskontroll

Appen bruker GitHub Releases som kilde for tilgjengelige versjoner. Den sammenligner installert BuildConfig.VERSION_NAME mot siste release-tag og viser oppdateringsbanner når en nyere versjon finnes.

## Datasikkerhet

KOVA Companion bruker fortsatt bare offentlig KOVA-data. Røde Kors-passord, Okta-cookies og private KOVA-data behandles ikke.

## Produksjonsstatus

Firebase Cloud Messaging er verifisert ende-til-ende på fysisk Android-enhet.

En reell ny Ullensaker-aktivitet ble senere fanget av Bridge som +1 og sendt via FCM etter at den ble synlig i offentlig KOVA. Polling er derfor redusert fra 15 til 5 minutter.

## Videre plan

- aktivere permanent Android release-signering med GitHub secrets
- endringshistorikk/audit-logg i Bridge
- retry/backoff og tydeligere driftsstatus
- flere hjelpekorps uten ny APK
