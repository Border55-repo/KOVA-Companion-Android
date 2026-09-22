# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.7.0 – Bridge Reliability

Denne versjonen gjør varslingskjeden robust nok for daglig bruk.

### Varslingssikkerhet
- samme KOVA-endring får en stabil `changeId`
- Android og Bridge beregner samme fingerprint
- FCM og lokal WorkManager-fallback dedupliseres mot hverandre
- samme aktivitet skal derfor ikke varsles to ganger selv om begge kanalene oppdager endringen
- Firebase har retry/backoff ved HTTP 429/5xx

### Varig push-kø
- nye/endret/fjernede aktiviteter legges i en persistent push-kø
- hvis Firebase feiler, ligger endringen som `pending`
- neste Bridge-kjøring prøver igjen selv om KOVA-snapshotet allerede er oppdatert
- sendte change IDs beholdes som dedup-historikk

### Endringshistorikk
- Bridge lagrer faktisk observerte KOVA-endringer per korps under `bridge/data/history/`
- historikken inneholder changeId, tidspunkt, type endring og aktivitetsdata
- historikken begrenses for å unngå ukontrollert repo-vekst

### Sikrere KOVA-henting
- KOVA-henting bruker retry/backoff
- svært kort HTML-respons avvises
- tom kalender avvises dersom et etablert snapshot tidligere hadde aktiviteter
- ekstreme plutselige fall i event-antall avvises som mistenkelig respons
- siste fungerende snapshot beholdes ved feil

### Bridge health
- `bridge/data/health.json` viser faktisk Bridge-status
- status: ok / degraded / error
- siste vellykkede kjøring
- siste helt vellykkede kjøring
- antall påfølgende feilkjøringer
- antall ventende push-meldinger
- siste push-tidspunkt
- status per hjelpekorps
- Android leser health-filen direkte og bruker GitHub Actions bare som fallback

## Polling

KOVA Bridge kontrollerer offentlig KOVA hvert 5. minutt.

KOVA kan ha en publiseringsforsinkelse mellom lagring i KOVA og tidspunktet arrangementet blir synlig i offentlig kalender. Bridge kan først oppdage aktiviteten når den offentlige kalenderen faktisk viser den.

Lokal Android WorkManager-synk kjører fortsatt hvert 15. minutt som fallback.

## Pushflyt

1. KOVA publiserer aktiviteten offentlig.
2. Bridge oppdager diff.
3. Endringen får stabil `changeId`.
4. Endringen skrives til historikk og persistent push-kø.
5. Bridge forsøker data-only FCM.
6. Ved suksess fjernes endringen fra pending-køen.
7. Android kontrollerer lokale varslingsfiltre.
8. Android dedupliserer changeId.
9. Trykk på varselet åpner riktig aktivitetsdetalj.

## Firebase

Firebase Android-klientkonfigurasjonen er registrert for:

- `no.juliannordli.kovacomp`

Bridge bruker privat GitHub repository secret:

- `FIREBASE_SERVICE_ACCOUNT_JSON`

Servicekonto eller privat nøkkel skal aldri legges inn i repositoryet.

## Release-signering

Release-workflowen bruker disse GitHub Actions-secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Release-signeringen er verifisert med Android `apksigner`.

Ved release bygges:

- signert APK
- Android App Bundle (AAB)
- SHA256SUMS.txt
- GitHub Release

Private signing keys skal aldri committes til repositoryet.

## Oppdateringskontroll

Appen bruker GitHub Releases som kilde for tilgjengelige versjoner. Den sammenligner installert `BuildConfig.VERSION_NAME` mot siste release-tag og viser oppdateringsbanner når en nyere versjon finnes.

## Datasikkerhet

KOVA Companion bruker bare offentlig KOVA-data. Røde Kors-passord, Okta-cookies og private KOVA-data behandles ikke.

## Produksjonsstatus

- Firebase Cloud Messaging er verifisert på fysisk Android-enhet
- push → korrekt aktivitetsdetalj er verifisert
- permanent release-signering er aktiv
- Bridge poller hvert 5. minutt
- Bridge Reliability v0.7 har persistent kø, historikk, deduplisering og health-status

## Videre plan

- dynamisk korpsregister og flere samtidige korps
- personlige favoritter / «Mine aktiviteter»
- lokale aktivitets-påminnelser
- uke- og månedsvisning
- Google Play intern/closed testing
- ikon, splash, onboarding og v1.0-polering
