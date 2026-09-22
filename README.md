# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.2.0
- Nytt Android-dashboard i Jetpack Compose
- Ullensaker, Eidsvoll/Hurdal, Nittedal og Skedsmo som ferdige korpsvalg
- Neste aktivitet på forsiden
- Filtrering på aktivitetstype
- Legg arrangement direkte i Android-kalender
- Åpne arrangementets KOVA-kilde
- Varslingsvalg for nye, endrede og fjernede aktiviteter
- Valg for å vise/skjule historiske aktiviteter
- Lokal cache og bakgrunnssynk via WorkManager
- Automatisk GitHub Actions-bygg av APK

## Datasikkerhet
Denne versjonen bruker bare offentlig KOVA-data. Den lagrer ikke Røde Kors-passord, Okta-cookies eller private KOVA-data.

## Videre plan
- KOVA Bridge for mer umiddelbar serverstyrt varsling
- Flere hjelpekorps
- Senere autentisert KOVA-modul dersom offisiell API/OIDC-tilgang blir tilgjengelig
