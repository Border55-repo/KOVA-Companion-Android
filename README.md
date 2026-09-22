# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.8.0 – Alle korps / dynamisk register

KOVA Companion henter nå korpslisten dynamisk fra KOVAs offentlige organisasjonsside.

### Korpsregister
- Bridge oppdager KOVA-enheter automatisk fra `https://www.kova.no/Events.aspx`
- produksjonstesten fant 42 offentlige KOVA-enheter
- 38 av disse er klassifisert som hjelpekorps
- appen viser alle tilgjengelige hjelpekorps i korpsvelgeren
- nye korps som dukker opp i offentlig KOVA kan legges til registeret uten ny Android-versjon
- de fire opprinnelige korpsene beholdes som offline-fallback dersom registeret ikke kan hentes

### Flere korps samtidig
- brukeren kan velge flere hjelpekorps under Innstillinger
- Firebase abonnerer på ett topic per valgt korps
- FCM kontrolleres i tillegg lokalt mot valgt korps
- hovedkalender og push-abonnement er nå separate konsepter
- du kan for eksempel vise Ullensaker som hovedkalender og samtidig få varsler fra andre korps

### Skalerbar polling
- Ullensaker, Eidsvoll/Hurdal, Nittedal og Skedsmo beholder 5-minutters prioritet
- øvrige offentlige KOVA-enheter fordeles i fire stabile Bridge-bøtter
- hvert ikke-prioritert korps kontrolleres omtrent hvert 20. minutt
- dette gir landsdekkende støtte uten å hente alle kalendere hvert 5. minutt
- Android-fallbacken fordeler også valgte korps i rotasjon og prioriterer korpset som er åpent i appen

### Sikker onboarding
- første snapshot for et nytt korps behandles alltid som baseline
- eksisterende kalenderaktiviteter blir ikke varslet som nye når korpset tas inn første gang
- baseline-historikk blir ikke lagt i push-kø
- produksjonstest bekreftet 0 falske push og 0 pending etter onboarding

## v0.7.0 – Bridge Reliability

### Varslingssikkerhet
- samme KOVA-endring får stabil `changeId`
- Android og Bridge beregner samme fingerprint
- FCM og lokal WorkManager-fallback dedupliseres mot hverandre
- Firebase har retry/backoff ved HTTP 429/5xx

### Varig push-kø
- nye/endret/fjernede aktiviteter legges i persistent push-kø
- hvis Firebase feiler, ligger endringen som `pending`
- neste Bridge-kjøring prøver igjen selv om KOVA-snapshotet allerede er oppdatert
- sendte change IDs beholdes som dedup-historikk

### Endringshistorikk
- Bridge lagrer observerte KOVA-endringer per korps under `bridge/data/history/`
- historikken inneholder changeId, tidspunkt, type endring og aktivitetsdata

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
- antall påfølgende feilkjøringer
- antall ventende push-meldinger
- siste push-tidspunkt
- status per pollrunde
- Android leser health-filen direkte og bruker GitHub Actions som fallback

## KOVA-varslingsflyt

1. KOVA publiserer aktiviteten offentlig.
2. Bridge oppdager korps og kalendere fra KOVAs offentlige sider.
3. Relevant kalender polles etter sin rotasjon.
4. Endringen får stabil `changeId`.
5. Endringen skrives til historikk og persistent push-kø.
6. Bridge forsøker data-only FCM til korpsets topic.
7. Android kontrollerer valgte korps og lokale varslingsfiltre.
8. Android dedupliserer changeId.
9. Trykk på varselet åpner riktig aktivitetsdetalj.

KOVA kan ha en publiseringsforsinkelse mellom lagring i KOVA og tidspunktet aktiviteten blir synlig offentlig.

## Firebase

Firebase Android-klientkonfigurasjonen er registrert for:

- `no.juliannordli.kovacomp`

Bridge bruker privat GitHub repository secret:

- `FIREBASE_SERVICE_ACCOUNT_JSON`

## Release-signering

Release-workflowen bruker:

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

## Oppdateringskontroll

Appen bruker GitHub Releases som kilde for tilgjengelige versjoner og viser oppdateringsbanner når en nyere release finnes.

## Datasikkerhet

KOVA Companion bruker bare offentlig KOVA-data. Røde Kors-passord, Okta-cookies og private KOVA-data behandles ikke.

## Videre plan

- bedre søk/filter i korpslisten
- personlige favoritter / «Mine aktiviteter»
- lokale aktivitets-påminnelser
- uke- og månedsvisning
- Google Play intern/closed testing
- ikon, splash, onboarding og v1.0-polering
