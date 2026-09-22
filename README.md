# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.3.0
- KOVA Bridge med sentral kontroll av offentlige KOVA-kalendere hvert 15. minutt
- Android bruker Bridge JSON først
- Automatisk fallback til direkte KOVA dersom Bridge er utilgjengelig
- Datakilden vises i appen
- Lokal bakgrunnssynk hvert 15. minutt
- Ullensaker, Eidsvoll/Hurdal, Nittedal og Skedsmo
- Neste aktivitet, filtre, kalenderhandling og KOVA-lenke
- Varslingsvalg for nye, endrede og fjernede aktiviteter
- Lokal cache for offline-visning

## Bridge
Bridge-koden ligger under `bridge/`.

GitHub Actions-workflowen `kova-bridge.yml`:
1. kjører parser-tester,
2. henter offentlige KOVA-kalendere,
3. lager normaliserte JSON-snapshots,
4. committer kun når KOVA-data faktisk endres.

Android-klienten leser snapshots fra `bridge/data/`.

## Datasikkerhet
Denne versjonen bruker bare offentlig KOVA-data. Den lagrer ikke Røde Kors-passord, Okta-cookies eller private KOVA-data.

## Neste steg
- Firebase Cloud Messaging for umiddelbare pushvarsler fra Bridge
- Flere hjelpekorps
- Senere autentisert KOVA-modul dersom offisiell API/OIDC-tilgang blir tilgjengelig
