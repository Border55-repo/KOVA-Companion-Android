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
- [ ] offline-bruk på fysisk iPhone
- [ ] kalenderflyt på fysisk iPhone

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

Status: [~] Implementert, fysisk admin-test pågår

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
- [x] cachekontroll er quality-testet; fysisk admin-test gjenstår
- [ ] førstegangs passordbytte er fysisk testet

---

## v1.4 – Mine vakter 2.0

Status: [ ] Planlagt

Mål:
- bedre favoritter
- egendefinerte påminnelser
- enklere oversikt når brukeren har mange vakter
- lokale notater per vakt
- bedre kalenderflyt
- kommende favorittvakter tydeligere på forsiden

Ferdig når:
- [ ] favoritter er stabile over synk og oppdatering
- [ ] påminnelser kan tilpasses
- [ ] Mine vakter fungerer godt med mange aktiviteter
- [ ] kalenderintegrasjon er fysisk kvalitetssikret

---

## v1.5 – Varsler 2.0

Status: [ ] Planlagt

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

Status: [ ] Planlagt

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

Status: [ ] Planlagt

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

Status: [ ] Planlagt

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

Status: [ ] Planlagt

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

Status: [ ] Planlagt

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

Status: [ ] Fremtid

Retning:
- større arkitekturgjennomgang
- mer robust Bridge/backend
- sanntidssynk der KOVA tillater det
- videre personalisering uten unødvendig kontoavhengighet
- bedre skalerbarhet
- utvikling basert på faktisk 1.x-bruk

v2.0 skal ikke startes før 1.x-serien har bevist stabil drift og de viktigste brukerbehovene er kjent.

---

## Nærmeste prioritering

1. **v1.3 KOVA Admin & drift**
2. Fysisk iPhone-test av offline og kalender
3. **v1.4 Mine vakter 2.0**
4. **v1.5 Varsler 2.0**
5. Google Play intern test når brukerflyten er moden nok

Kritiske feil, synkproblemer og manglende vakter skal alltid prioriteres foran nye funksjoner.
