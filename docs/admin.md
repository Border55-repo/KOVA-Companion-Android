# KOVA Admin

KOVA Admin er driftspanelet for KOVA Companion.

Live URL:
- https://border55-repo.github.io/KlarX/kova/admin/

## Ferdig

- innlogging som `superuser`
- tvunget passordbytte ved første innlogging
- live Bridge-status og siste vellykkede synk
- antall hjelpekorps og snapshot-aktiviteter
- pending push og siste push
- registrerte PWA Web Push-abonnementer
- siste Android-release
- status og aktivitetstall per hjelpekorps
- fjernstyrt PWA cache-generasjon
- link fra både Android og PWA
- Firestore-regler som skiller offentlig PWA-data fra admin-data
- Quality-test av admin-JavaScript og kritisk login/cache-flyt

## Engangsbootstrap

Firebase Authentication må ha Email/Password aktivert, og én bruker må opprettes:

- brukernavn i panelet: `superuser`
- Firebase e-post: `superuser@kova-companion.local`
- midlertidig passord: velges ved opprettelse og skal byttes umiddelbart ved første innlogging

Det midlertidige passordet skal ikke lagres i frontend, GitHub-kode eller dokumentasjon.

GitHub-servicekontoen mangler prosjekt-rettigheten som kreves for å aktivere Firebase Auth-tjenesten automatisk. Firestore-regler og offentlig PWA-cachekontroll er allerede deployet.

## Cache refresh

Admin-knappen **Publiser ny PWA-cache** øker en offentlig, ikke-sensitiv cache-generasjon i Firestore. PWA-klienter sjekker denne automatisk når de åpnes eller kommer i forgrunnen. Hvis generasjonen er nyere enn den lokale, slettes gammel KOVA-PWA-cache, service worker sjekkes på nytt og klienten lastes inn på nytt.

Vanlige brukere skal derfor ikke måtte tømme cache manuelt.
