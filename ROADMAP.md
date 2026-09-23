# KOVA Companion – Roadmap

KOVA Companion er nå i stabil 1.x-utvikling. Videre arbeid skal først og fremst gjøre det enklere å følge kommende vakter, samtidig som teknisk drift og diagnostikk flyttes bort fra vanlige brukere og inn i et eget adminmiljø.

## Prinsipper

- Brukeren skal møte kommende vakter først – ikke teknisk status.
- Vanlig bruk skal kreve minst mulig konfigurasjon.
- Synk, cache, push og PWA-oppdatering skal i størst mulig grad skje automatisk.
- Teknisk diagnostikk, cachekontroll og backend-status skal ligge i KOVA Admin.
- Ingen Android-release uten grønn CI.
- Android-versjoner følges helt frem til signert APK/AAB, signaturkontroll, GitHub Release og korrekt `app-update.json`.
- Push, synk og oppdateringsløp skal ikke brytes av UI-endringer.
- Reelle brukerbehov og feil fra faktisk bruk prioriteres foran pynt.
- KOVA Companion er et uavhengig prosjekt. Prosjekteier: Julian Nordli.

---

## PWA / iPhone

Status: [x] Operativ

Live:
- https://border55-repo.github.io/KlarX/kova/

Levert:
- [x] installérbar PWA
- [x] hjelpekorpsvalg
- [x] følge flere korps
- [x] Fulgte-visning
- [x] Mine vakter / favoritter
- [x] søk og aktivitetstypefilter
- [x] 7- og 30-dagers visning
- [x] Neste vakt / Denne uka / Senere
- [x] deling og kalenderfil
- [x] Web Push for ny, endret og fjernet aktivitet
- [x] fysisk iPhone-test av installasjon og mottatt Web Push
- [x] samtidig fysisk Android FCM + iPhone Web Push-test
- [x] sikkert write-only abonnementregister
- [x] automatisk push-reparasjon
- [x] automatisk refresh når appen kommer i forgrunnen
- [x] automatisk refresh hvert 5. minutt mens PWA-en brukes
- [x] cache-busting av ferske KOVA-data
- [x] automatisk service-worker-oppdatering
- [x] offline app-shell og fallback til siste tilgjengelige data
- [x] synlig prosjekteier og prosjektstatus

Gjenstår fysisk kvalitetssikring:
- [x] offline-bruk på fysisk iPhone
- [x] kalenderflyt på fysisk iPhone

---

## v1.1 – Stabilitet og drift

Status: [x] Levert gjennom 1.1.x

Levert:
- [x] sikker snarvei til KOVA / Røde Kors-innlogging
- [x] FCM pushdiagnostikk
- [x] fysisk Android-pushtest
- [x] fysisk samtidig Android/PWA-pushtest
- [x] Bridge-health
- [x] signert releasepipeline
- [x] manifestbasert appoppdatering
- [x] cache-bypass for ferske Bridge-data
- [x] håndtering av nye vakter uten appoppdatering
- [x] prosjekteierinformasjon i Android og PWA

Videre stabilitetsarbeid fortsetter løpende og trenger ikke egen hovedversjon.

---

## v1.2 – Enkel hverdag

Status: [x] Levert i Android 1.2.0 / PWA 1.8

Mål: åpne appen og forstå kommende vakter umiddelbart.

Levert:
- [x] Neste vakt øverst
- [x] Denne uka
- [x] Senere (30 dager)
- [x] Mine aktiviteter omdøpt til Mine vakter
- [x] teknisk status tones ned for vanlige brukere
- [x] teknisk Appstatus vises primært via Innstillinger
- [x] søk, filtre, favoritter og kalender beholdt
- [x] automatisk PWA-refresh uten krav om brukerhandling
- [x] ferske KOVA-data prioriteres foran gammel CDN/cache

---

## v1.3 – KOVA Admin & drift

Status: [x] Levert og fysisk testet

Mål:
- eget backend/adminpanel
- knapp til KOVA Admin fra Android og PWA
- superuser-konto
- midlertidig førstegangspassord håndteres sikkert
- tvunget passordbytte ved første innlogging
- live Bridge-status
- siste vellykkede synk
- antall hjelpekorps og aktiviteter
- siste push og pending push
- registrerte PWA-abonnementer
- Android siste release og update-manifest
- status per hjelpekorps
- manuell Bridge-refresh / synkkontroll
- kontrollert PWA cache/service-worker refresh
- tydelig visning av feil og degraderte korps
- adminfunksjoner skal ikke eksponeres for vanlige brukere

Sikkerhetskrav:
- [x] ingen adminpassord hardkodes i frontend eller offentlig repo
- [x] innlogging håndteres Firebase-basert
- [x] første innlogging krever nytt passord
- [x] admin-data krever autentisert superuser
- [x] vanlige PWA-brukere kan ikke lese admin- eller abonnementsdata

Ferdig når:
- [x] adminpanelet er live
- [x] Android-knapp åpner adminpanelet
- [x] PWA-knapp åpner adminpanelet
- [x] live statistikk henter Bridge-data og Firestore-status
- [x] cachekontroll er quality-testet og fysisk testet
- [x] førstegangs passordbytte er fysisk testet
- [x] Bridge-synkknappen er fysisk testet i adminpanelet

---

## v1.4 – Mine vakter 2.0

Status: [x] Levert i Android 1.4.0 / PWA 1.12

Mål:
- [x] bedre favoritter
- [x] egendefinerte påminnelser
- [x] enklere oversikt når brukeren har mange vakter
- [x] lokale notater per vakt
- [x] bedre kalenderflyt
- [x] kommende favorittvakter tydeligere på forsiden

Levert:
- [x] favoritter følger samme aktivitet gjennom vanlige KOVA-endringer
- [x] Android-påminnelser: 30 min, 1 t, 2 t, 6 t og 24 t før
- [x] PWA-påminnelser via Bridge/Web Push: 15 min til 2 dager før
- [x] server-side deduplisering av PWA-påminnelser
- [x] valgt påminnelse legges også inn som VALARM i PWA-kalenderfil
- [x] lokale notater per vakt i Android og PWA
- [x] Mine vakter grupperes og kan lastes trinnvis ved mange aktiviteter
- [x] neste favorittvakt vises tydelig på PWA-forsiden

Ferdig når:
- [x] favoritter er stabile over synk og oppdatering
- [x] påminnelser kan tilpasses
- [x] Mine vakter fungerer godt med mange aktiviteter
- [x] kalenderintegrasjon er fysisk kvalitetssikret

---

## v1.5 – Varsler 2.0

Status: [x] Levert i 1.x-sluttpakken

Mål:
- varsler per hjelpekorps
- varsler per aktivitetstype
- stille perioder
- varselhistorikk
- bedre varsling for endret og fjernet aktivitet
- tydelig kobling fra varsel til riktig aktivitet
- forbedret dedupe

Ferdig når:
- [ ] ingen kjente duplikatvarsler
- [ ] riktig aktivitet åpnes fra varsel
- [ ] varsler kan tilpasses per korps og aktivitetstype
- [ ] varsling er testet med minst to korps

---

## v1.6 – Flere korps og skalering

Status: [x] Levert i 1.x-sluttpakken

Mål:
- bedre støtte for mange fulgte korps
- favorittkorps
- raskere korpssøk
- gruppering etter distrikt der data er tilgjengelig
- optimalisert synk ved mange abonnement
- bedre oversikt over datakvalitet per korps

Ferdig når:
- [ ] appen håndterer flere samtidige korps uten merkbar treghet
- [ ] topic-abonnement synkroniseres stabilt
- [ ] korpsvalg er enkelt også ved stor liste

---

## v1.7 – Arrangementdetaljer 2.0

Status: [x] Levert i 1.x-sluttpakken

Mål:
- forbedret vaktdetaljside
- tydeligere dato, klokkeslett og aktivitetstype
- kart/adresse når KOVA-data støtter det
- kontaktinformasjon når tilgjengelig
- bedre deling
- tydelig visning av hva som er endret

Ferdig når:
- [ ] detaljsiden gir relevant informasjon uten å måtte åpne KOVA
- [ ] manglende KOVA-felt håndteres ryddig
- [ ] deling og kalenderflyt fungerer stabilt

---

## v1.8 – Offline og robusthet

Status: [x] Levert i 1.x-sluttpakken

Mål:
- full lokal cache av siste synk
- lesetilgang uten nett
- tydelig, men enkel offline-status
- synk-kø ved manglende nett
- automatisk gjenoppretting når nett kommer tilbake
- kontrollert overgang mellom Bridge og direkte KOVA
- ingen manuell cachehåndtering for vanlige brukere

Ferdig når:
- [ ] siste kjente vakter alltid kan åpnes offline
- [ ] appen synkroniserer automatisk når nett kommer tilbake
- [ ] brukeren ser enkelt når data sist ble oppdatert
- [ ] fysisk iPhone offline-test er bestått

---

## v1.9 – UI 2.0 og tilgjengelighet

Status: [x] Levert i 1.x-sluttpakken

Mål:
- mer konsekvent design
- enklere navigasjon
- forbedret dark mode
- bedre nettbrettstøtte
- tilgjengelighetsgjennomgang
- tekststørrelse, kontrast og trykkflater
- videreutvikle blålys/KOVA-profil uten å svekke lesbarheten

Ferdig når:
- [ ] hovedflytene fungerer godt på små og store skjermer
- [ ] dark mode er kvalitetssikret
- [ ] tilgjengelighetsgjennomgang er gjennomført

---

## v1.10 – Distribusjon og Google Play

Status: [~] Teknisk klargjort – Play Console-test avhenger av ekstern konto/servicekonto

Mål:
- intern Google Play-test
- lukket testspor
- Play Store-metadata
- screenshots
- release notes
- Data Safety-kontroll
- automatisert eller forenklet Play-release

Ferdig når:
- [ ] intern test er bestått
- [ ] lukket testspor fungerer
- [ ] AAB kan distribueres uten manuelle feil
- [ ] Play Store-listing er klar

---

## v2.0 – KOVA Companion 2

Status: [~] 2.0.0 releasekandidat bygges

Retning:
- større arkitekturgjennomgang
- mer robust Bridge/backend
- sanntidssynk der KOVA tillater det
- videre personalisering uten unødvendig kontoavhengighet
- bedre skalerbarhet
- utvikling basert på faktisk 1.x-bruk

2.0 bygger videre på den ferdige 1.x-funksjonaliteten med en eksplisitt Bridge 2.0-kompatibilitetskontrakt, capability discovery og fortsatt bakoverkompatible snapshots.

---

## Nærmeste prioritering

1. **Fullfør grønn 2.0.0 CI og signert release**
2. **Fysisk sluttkontroll på Android og iPhone/PWA**
3. **Google Play intern/lukket test når Play Console-servicekonto er tilgjengelig**
4. **Samle faktisk 2.0-bruk og prioritere videre arbeid derfra**

Kritiske feil, synkproblemer og manglende vakter skal alltid prioriteres foran nye funksjoner.
