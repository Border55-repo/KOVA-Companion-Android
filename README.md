## v0.10.0 – v1.0-polering påbegynt

- første gangs onboarding
- forklarer korpsvalg, varsler, favoritter og personvern før normal bruk
- varslingssamtykke flyttes inn i oppstartssekvensen
- neste del av hovedplanen er appikon, splash og Google Play testklargjøring

## v0.9.5 – Søk, kalender og mobilforbedringer

- fikser handlingsknapper på smale mobilskjermer
- søk i aktiviteter på navn, type, dato og tid
- søk i hjelpekorpslisten
- ny liste-/kalendervisning med ukegruppering
- beholder KOVA-aktiviteter uten oppgitt klokkeslett
- støtter flere offentlige aktivitetstyper fra KOVA, blant annet Aksjon og RØFF
- bygger videre mot v1.0 uten å endre den eksisterende Bridge-/pusharkitekturen

## v0.9.4 – Oppdateringsverifisering

- bruker offentlig `app-update.json` i stedet for GitHub Release API i selve appen
- unngår 403/rate-limit-feilen fra den gamle oppdateringssjekken
- laget som kontrollversjon slik at v0.9.3 kan oppdage v0.9.4 direkte i appen
- signert med samme Android-nøkkel som tidligere releaser

# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.9.0 – Mine aktiviteter

Denne versjonen legger et personlig aktivitetslag oppå multi-korpssystemet.

### Favoritter
- alle KOVA-aktiviteter kan markeres som favoritt
- favoritter lagres lokalt med korps + stabil semantisk aktivitetsidentitet
- favoritter overlever endring av dato/tid så lenge aktiviteten ellers er den samme
- hvis en aktivitet fjernes fra KOVA, fjernes den også fra Mine aktiviteter og lokale påminnelser avbrytes

### Mine aktiviteter
- samlet visning på tvers av alle korps
- viser korpsnavn på hvert favorittkort
- filtre for Alle, 7 dager og 30 dager
- sortering på dato, tid og aktivitet
- åpne detaljer direkte fra samlet visning
- legg aktivitet i kalender eller fjern favoritt med ett trykk

### Lokale påminnelser
- 24 timer før favorittaktivitet
- 2 timer før favorittaktivitet
- begge kan slås av/på separat i Innstillinger
- påminnelser bruker Android WorkManager
- trykk på påminnelsen åpner riktig aktivitet
- påminnelser reskjema-legges automatisk hvis aktivitetens dato eller klokkeslett endres
- Android kan forskyve et lokalt varsel noe ved strømsparing

## v0.8.0 – Alle korps / dynamisk register

- Bridge oppdager KOVA-enheter automatisk fra offentlig KOVA
- produksjonstest: 42 offentlige enheter, 38 hjelpekorps
- flere korps kan følges samtidig
- ett hovedkorps kan vises i kalenderen mens varsler mottas fra andre
- Firebase topic per valgt korps
- prioriterte korps poller hvert 5. minutt, øvrige i rotasjon
- Android fallback fordeles også i rotasjon
- første snapshot er silent baseline og sender ikke falske «nye aktivitet»-varsler

## v0.7.0 – Bridge Reliability

- stabil `changeId` mellom Bridge og Android
- deduplisering mellom FCM og lokal fallback
- persistent push-kø
- retry/backoff ved Firebase/KOVA-feil
- endringshistorikk per korps
- beskyttelse mot tom eller mistenkelig KOVA-respons
- egen `bridge/data/health.json`

## KOVA-varslingsflyt

1. KOVA publiserer aktiviteten offentlig.
2. Bridge oppdager relevante korps og kalendere.
3. Kalenderen polles etter sin rotasjon.
4. Endringen får stabil `changeId`.
5. Bridge lagrer endringen og sender data-only FCM.
6. Android kontrollerer valgte korps og lokale filtre.
7. Android dedupliserer changeId.
8. Trykk på varselet åpner riktig aktivitet.
9. Favorittaktiviteter kan i tillegg få lokale 24t/2t-påminnelser.

KOVA kan ha en kort publiseringsforsinkelse mellom lagring og synlighet i offentlig kalender.

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

Appen leser et offentlig `bridge/data/app-update.json`-manifest som release-workflowen oppdaterer automatisk, og viser oppdateringsbanner når en nyere release finnes. Selve oppdateringssjekken bruker derfor ikke GitHub Release API.

## Datasikkerhet

KOVA Companion bruker bare offentlig KOVA-data. Røde Kors-passord, Okta-cookies og private KOVA-data behandles ikke.

## Videre plan

- bedre søk/filter i korpslisten
- kalenderlignende uke-/månedsoppsett
- søk i aktiviteter
- Google Play intern/closed testing
- ikon, splash, onboarding og v1.0-polering
