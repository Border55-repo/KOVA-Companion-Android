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


## Admin Drift 1.1

Adminpanelet har nå et live driftslag i Firestore.

- systemhelse vises som Grønn / Advarsel / Feil
- Bridge skriver live runtime-status ved hver kjøring
- status viser siste kjøring, poll-antall, feil, pending push og fullsynk
- korps får egen siste-kontroll-status uten å blande dette med tidspunktet KOVA-data sist endret seg
- korps eldre enn 35 minutter markeres som gamle
- admin kan be om full Bridge-synk
- synkforespørselen lagres autentisert i Firestore og plukkes opp av neste 5-minutters Bridge-runde
- fullsynk er ende-til-ende testet med 42/42 organisasjoner og 0 feil
- live runtime viste 42 organisasjonskontroller etter fullsynk
- anonyme brukere kan verken lese admin-runtime eller lese/skrive Bridge-synkkommandoer

Ingen GitHub-token, Firebase service account eller annen backendhemmelighet eksponeres i admin-nettleseren.


## Admin Drift 1.2

- Bridge-synkknappen overvåkes automatisk i adminpanelet
- status går synlig gjennom `requested` → `running` → `completed` / `failed`
- knappen låses mens synk pågår, slik at samme fullsynk ikke bestilles flere ganger ved et uhell
- ferdig status viser tidspunkt, antall kontrollerte korps og antall feil
- dashboardet oppdateres hvert 10. sekund mens en fullsynk pågår og hvert 60. sekund ellers
- adminressurser hentes network-first slik at driftspanelet ikke blir hengende på gammel PWA-cache
- fysisk cache-generasjonstest er godkjent med cache-generasjon 3
- gjenstående v1.3-kvalitetssikring: fysisk test av Bridge-synkknappen i adminpanelet
