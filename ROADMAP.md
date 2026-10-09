# Roadmap for Kova Companion

Kova Companion videreutvikles som webapp for iPhone og Android.
Den native Android-appen er avsluttet og fjernet fra aktiv kildekode.

## Levert

- [x] Stabil KOVA Bridge med datakontroll og varsler.
- [x] Installerbar PWA med kalender, Mine vakter, notater og varselhistorikk.
- [x] iPhone-tilpasning for kalender og datovalg i PWA 2.3.3.
- [x] Egen GitHub Action for gjennomgått endringslogg og PWA-push etter publisering.
- [x] Avvikle Android-APK, FCM-korpskanaler og tilhørende CI.

## Neste kontroller

- [ ] Bekreft kalenderoppsett og personlig push på fysisk iPhone.
- [ ] Bekreft PWA-installasjon og personlig push på fysisk Android.
- [ ] Kontroller leveringsstatus for hver release-push; push-tjenestens aksept er ikke bevis på visning.
- [ ] Følg Bridge-status, feil og abonnementskvalitet i adminpanelet.

Ved hver brukerrettet oppdatering: publiser PWA, verifiser live-versjonen,
og send én kort endringslogg med push til PWA-abonnement.

