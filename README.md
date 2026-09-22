# KOVA Companion Android

Uoffisiell Android-app for offentlig KOVA-kalenderdata.

## v0.5.0
- Trykk på pushvarsel åpner riktig aktivitet i appen
- Egen detaljside for aktiviteter
- Push bruker data-only FCM slik at appens filtre alltid respekteres
- Varslingsfiltre for nye, endrede og fjernede aktiviteter
- Varslingsfiltre per aktivitetstype
- Bridge health/status vises i appen fra siste GitHub Actions-kjøring
- KOVA Bridge sjekker offentlige KOVA-kalendere hvert 15. minutt
- Android bruker Bridge først og direkte KOVA som fallback
- Lokal WorkManager-synk hvert 15. minutt beholdes som sikkerhetsnett
- Ullensaker, Eidsvoll/Hurdal, Nittedal og Skedsmo støttes

## Pushflyt

Produksjonsflyten er:

1. KOVA Bridge oppdager en reell endring.
2. Bridge sender data-only FCM via HTTP v1.
3. Android mottar meldingen i KovaFirebaseMessagingService.
4. Appen sjekker brukerens varslingstype- og aktivitetstypefiltre.
5. Varslet opprettes lokalt med riktig event-ID og korps.
6. Trykk på varselet åpner detaljvisningen for riktig aktivitet.

Ved store endringer begrenses push-bursts for å unngå varselspam.

## Firebase-oppsett

Firebase Android-klientkonfigurasjonen er registrert i appen for package no.juliannordli.kovacomp.

Bridge bruker privat GitHub repository secret:

- FIREBASE_SERVICE_ACCOUNT_JSON

Servicekonto eller privat nøkkel skal aldri legges inn i repositoryet.

## Topic-format

App og Bridge bruker samme topic-format:

- kova_ullensakerrkh
- kova_ehrkh
- kova_nittedal_rkh
- kova_skedsmo_rkh

## Datasikkerhet

KOVA Companion bruker fortsatt bare offentlig KOVA-data. Røde Kors-passord, Okta-cookies og private KOVA-data behandles ikke.

## Produksjonsstatus

Firebase Cloud Messaging er verifisert ende-til-ende på fysisk Android-enhet.

Bridge health leses fra siste kova-bridge.yml workflow-run i det offentlige GitHub-repoet. Appen viser om Bridge er grønn, kjører eller har feilet.

## Videre plan

- signert release-APK/AAB og automatisk GitHub Release
- versjonssjekk i appen
- endringshistorikk/audit-logg i Bridge
- flere hjelpekorps uten ny APK
