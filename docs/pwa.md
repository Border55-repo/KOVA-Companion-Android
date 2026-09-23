# KOVA Companion PWA / iPhone

## Live-adresse

https://border55-repo.github.io/KlarX/kova/

## Installere på iPhone

1. Åpne live-adressen i Safari.
2. Trykk Del.
3. Velg **Legg til på Hjem-skjermen**.
4. Velg **Legg til**.
5. Start KOVA Companion fra ikonet på Hjem-skjermen.

PWA-en krever ingen Apple Developer-konto og distribueres uten App Store.

## PWA 1.1

- hjelpekorpsvalg
- søk og aktivitetstypefilter
- 7 dager / 30 dager
- Mine aktiviteter og favoritter
- følge flere hjelpekorps
- samlet Fulgte-visning
- del aktivitet
- legg aktivitet i kalender
- aktiviteter uten klokkeslett eksporteres som heldagsaktivitet
- offline app-shell og cache av siste KOVA-data
- Logg inn / Åpne KOVA
- automatisk Quality-test og GitHub Pages-deploy

## Push-status

Firebase Web App er opprettet i Firebase-prosjektet `kova-companion`.

Web Push-backenden er klargjort med:
- Firestore-provisjoneringsscript
- write-only sikkerhetsregler for PWA-abonnementer
- egen CI-workflow

Firestore API er foreløpig deaktivert. Servicekontoen som brukes av GitHub Actions har ikke tillatelsen `serviceusage.services.enable`, så prosjekt-eier må aktivere Firestore API én gang. Etter dette kan CI fullføre database og regler uten at Firebase-hemmeligheter eksponeres.

PWA-en viser ikke en halvferdig varselknapp før backend er klar.
