# KOVA Companion 1.x – sluttpakke før 2.0

Denne dokumentasjonen beskriver funksjonene som er samlet i sluttbygget av 1.x-serien.

## v1.5 – Varsler 2.0

Implementert:
- varsler per fulgt hjelpekorps
- filter for nye, endrede og fjernede aktiviteter
- filter per aktivitetstype
- stille perioder
- lokal varselhistorikk på Android
- lokal IndexedDB-varselhistorikk i PWA
- forbedret dedupe på Android og PWA
- full aktivitet kontekst ved varseltrykk
- endringsoppsummering følger endret aktivitet
- stale Web Push-abonnementer (404/410) deaktiveres trygt
- backend smoke-test mot UllensakerRKH og EHRKH for Android FCM og PWA Web Push

## v1.6 – Flere korps og skalering

Implementert:
- søk i hjelpekorps
- favorittkorps
- favorittkorps sorteres først
- støtte for distrikt dersom Bridge leverer feltet
- datakvalitet/status og antall aktiviteter fra Bridge index
- PWA begrenser parallelle korpskall
- Android bakgrunnssynk fordeler fulgte korps over intervaller
- PWA-abonnement støtter opptil 100 korps

## v1.7 – Arrangementdetaljer 2.0

Implementert:
- tydelig type, dato, klokkeslett og korps
- lokale notater
- favoritt/påminnelse/kalender
- sted vises og kan åpnes i kart når KOVA-teksten gir et tydelig sted
- kontaktinformasjon vises når e-post/telefon finnes i KOVA-teksten
- deling på Android og PWA
- endringsoppsummering fra varsler
- manglende strukturerte felt håndteres uten feil

## v1.8 – Offline og robusthet

Implementert:
- Android lokal cache per korps
- Bridge -> direkte KOVA fallback
- WorkManager venter på nett før synk
- PWA navigation/app-shell offline fallback
- normalisert Bridge-cache uten cache-busting-query
- automatisk gjenoppretting/refresh når nett kommer tilbake
- PWA varmer cache for valgt, fulgte og Mine-vakter-korps
- sist oppdatert/status beholdes synlig
- tidligere fysisk iPhone-offlinetest er bestått

## v1.9 – UI 2.0 og tilgjengelighet

Implementert:
- enklere PWA-hurtignavigasjon
- skip-link
- tydelig tastaturfokus
- minimum 44 px interaktive flater i PWA
- reduced-motion støtte
- responsive mobil-/nettbrettflater i PWA
- system dark mode på Android og PWA
- Android Material 3 og tekstbaserte handlinger
- Android lint som del av RC-gaten
- tilgjengelighetsgjennomgang dokumentert

Fysisk QA som ikke kan erstattes av CI:
- TalkBack
- VoiceOver
- stor tekst/font scaling
- reell nettbrettsenhet
- visuell dark-mode gjennomgang på flere enheter

## v1.10 – Distribusjon og Google Play

Klargjort:
- targetSdk 36
- signert AAB-releasepipeline
- Play Store-tekst på norsk
- release notes
- Data Safety-kontrollgrunnlag
- offentlig personvernside
- intern/lukket Google Play workflow
- testplan
- plan for ekte Store-skjermbilder

Ekstern avhengighet:
- Play Console-app/servicekonto må være koblet før GitHub kan publisere til testspor
- ekte Store-skjermbilder må tas fra faktisk Android-build
- intern og lukket test kan først markeres bestått etter installasjon/test via Google Play

## v2.0

Ikke startet. 2.0 skal bygge på faktisk erfaring fra den ferdige 1.x-serien.
