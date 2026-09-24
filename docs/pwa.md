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

Web Push-backenden er ferdig og bruker standard Web Push for best mulig PWA-kompatibilitet.

Ferdig:
- Firestore `(default)` i `eur3`
- write-only sikkerhetsregler for PWA-abonnementer
- klienter kan ikke lese abonnementregisteret
- ugyldige abonnement-writes blokkeres
- varig VAPID privatnøkkel ligger kun server-side i Firestore
- offentlig VAPID-nøkkel publiseres til PWA-en
- Bridge sender samme KOVA-endringer til Android FCM og PWA Web Push
- dedupe bruker samme `changeId`
- stale push-abonnement deaktiveres server-side
- egen live rules-probe i CI
- egen Web Push-smoketest-workflow

Gjenstår før PWA-push er fysisk godkjent:
1. installer PWA-en på iPhone
2. åpne appen fra Hjem-skjermen
3. trykk **Aktiver varsler**
4. tillat varsler
5. kjør PWA-smoketesten og bekreft mottatt varsel



## Fysisk push-test

Fysisk push-test: bestått.

- Android FCM mottatt på fysisk Android-enhet
- PWA Web Push mottatt på fysisk iPhone
- backend viste 1 registrert PWA-enhet og leverte til 1/1 abonnement
- samtidig dobbeltest Android + PWA er bekreftet mottatt

## Kodegjennomgang 24.09.2026

Tidligere fysisk test ovenfor gjelder en tidligere direkte smoketest, ikke den feilende adminflyten. Se [gjennomgangen](review-2026-09-24.md) for påviste feil, rettelser og hva som må bekreftes på nytt. Produksjons-PWA vedlikeholdes i KlarX `apps/kova/`; denne repoets `pwa/` er en eldre prototype.
