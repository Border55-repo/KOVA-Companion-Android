# KOVA Companion – Roadmap etter v1.0

KOVA Companion v1.0.0 er første stabile hovedrelease. Videre utvikling skal prioritere stabilitet, faktisk bruk i felt, bedre varsling og gradvis skalering.

## Prinsipper for videre utvikling

- Ingen release uten grønn CI.
- Hver release skal testes på fysisk Android-enhet.
- Push, synk og oppdateringsløp skal ikke brytes av UI-endringer.
- Hver versjon følges helt frem til publisert release med APK, AAB, signaturkontroll og korrekt `app-update.json`.
- Nye funksjoner skal ikke gå på bekostning av stabilitet.
- Reelle brukerbehov og feil fra faktisk bruk prioriteres foran pynt.

---

## KOVA Companion PWA / iPhone

Status: [~] Aktiv utvikling

Live:
- https://border55-repo.github.io/KlarX/kova/

Ferdig:
- [x] installérbar PWA for iPhone/Android-nettleser
- [x] offline app-shell og cache av siste KOVA-data
- [x] hjelpekorpsvalg og søk
- [x] 7-dagers og 30-dagers visning
- [x] følge flere hjelpekorps og samlet Fulgte-visning
- [x] favoritter på tvers av korps
- [x] aktivitetstypefilter
- [x] deling av aktivitet
- [x] kalenderfil, inkludert heldagsaktivitet når klokkeslett mangler
- [x] sikker snarvei til KOVA/Røde Kors-innlogging
- [x] Firebase Web App opprettet i eksisterende kova-companion-prosjekt
- [x] PWA Quality-test og automatisk GitHub Pages-deploy

Pågår:
- [x] Web Push-backend for Ny / Endret / Fjernet aktivitet
- [x] sikkert write-only abonnementregister for PWA-enheter
- [x] varig VAPID-nøkkel lagret server-side og offentlig nøkkel publisert
- [x] live Firestore rules-probe i CI
- [x] dedikert PWA Web Push-smoketest
- [x] samtidig fysisk Android FCM- og iPhone Web Push-test
- [x] fysisk iPhone-test av installasjon, abonnement og mottatt Web Push
- [ ] fysisk iPhone-test av offline og kalender

---

## v1.1 – Stabilitet og drift

Status: [~] Pågår

Mål:
- rydde test- og diagnosekode fra RC-perioden
- forbedre pushdiagnostikk og synkstatus
- bedre retry ved nettverksfeil
- tydeligere Bridge-health
- bedre håndtering av midlertidige KOVA-feil
- crash- og feilrapportering uten å samle unødvendige personopplysninger
- kvalitetssikre oppdateringsløpet fra 1.0.x
- sikker snarvei til KOVA / Røde Kors-innlogging

Ferdig når:
- [x] KOVA-innlogging åpnes sikkert uten at appen lagrer passord
- [ ] push og synk har tydelig status
- [ ] nettverksfeil håndteres uten at appen stopper
- [ ] ingen kjente kritiske 1.0-feil står åpne
- [ ] fysisk Android-test er bestått
- [ ] release pipeline er grønn

---

## v1.2 – Enkel hverdag

Status: [x] Levert i Android 1.2.0 / PWA 1.8

- [x] Neste vakt øverst
- [x] Denne uka
- [x] Senere (30 dager)
- [x] Mine vakter
- [x] teknisk status tones ned for vanlige brukere
- [x] automatisk PWA-refresh i forgrunn og hvert 5. minutt
- [x] søk, filtre, favoritter og kalender beholdt

---

## v1.3 – Mine vakter 2.0

Status: [ ] Planlagt

Mål:
- forbedre favoritter
- egendefinerte påminnelser
- bedre oversikt over kommende aktiviteter
- enklere filtrering av Mine aktiviteter
- mulighet for lokale notater per aktivitet
- bedre kalenderflyt

Ferdig når:
- [ ] favoritter er stabile over synk og oppdatering
- [ ] påminnelser kan tilpasses
- [ ] Mine aktiviteter fungerer godt med mange aktiviteter
- [ ] kalenderintegrasjon er kvalitetssikret

---

## v1.4 – Varsler 2.0

Status: [ ] Planlagt

Mål:
- varsler per hjelpekorps
- varsler per aktivitetstype
- stille perioder
- varselhistorikk
- bedre varsling for endret og fjernet aktivitet
- tydeligere kobling fra varsel til riktig aktivitet
- forbedret dedupe

Ferdig når:
- [ ] ingen kjente duplikatvarsler
- [ ] riktig aktivitet åpnes fra varsel
- [ ] varsler kan tilpasses per korps og aktivitetstype
- [ ] varsling er testet med minst to korps

---

## v1.5 – Flere korps og skalering

Status: [ ] Planlagt

Mål:
- bedre støtte for mange fulgte korps
- favorittkorps
- raskere korpssøk
- gruppering etter distrikt der data er tilgjengelig
- optimalisert synk ved mange abonnement
- bedre oversikt over hvilke korps som har oppdaterte data

Ferdig når:
- [ ] appen håndterer flere samtidige korps uten merkbar treghet
- [ ] topic-abonnement synkroniseres stabilt
- [ ] korpsvalg er enkelt også ved stor liste

---

## v1.6 – Arrangementdetaljer 2.0

Status: [ ] Planlagt

Mål:
- forbedret aktivitetsdetaljside
- tydeligere visning av dato, klokkeslett og aktivitetstype
- kart/adresse når KOVA-data støtter det
- kontaktinformasjon når tilgjengelig
- deling av aktivitet
- tydelig visning av hva som er endret

Ferdig når:
- [ ] detaljsiden gir all relevant informasjon uten å åpne KOVA
- [ ] manglende KOVA-felt håndteres ryddig
- [ ] deling og kalenderflyt fungerer stabilt

---

## v1.7 – Offline og robusthet

Status: [ ] Planlagt

Mål:
- full lokal cache av siste synk
- lesetilgang uten nett
- tydelig offline-status
- synk-kø ved manglende nett
- kontrollert overgang mellom Bridge og direkte KOVA

Ferdig når:
- [ ] siste kjente aktiviteter alltid kan åpnes offline
- [ ] appen synkroniserer igjen automatisk når nett kommer tilbake
- [ ] brukeren ser om data er ferske eller cachet

---

## v1.8 – Personlig Companion

Status: [ ] Planlagt

Mål:
- mer nyttig startside
- «Dette skjer denne uka»
- kommende favoritter
- samlet oversikt over påminnelser
- smartere prioritering av relevante aktiviteter
- personlige, lokale preferanser uten krav om konto

Ferdig når:
- [ ] startsiden gir relevant informasjon med ett blikk
- [ ] Companion-funksjonene fungerer uten innlogging
- [ ] personvernprinsippene fra 1.0 beholdes

---

## v1.9 – UI 2.0 og tilgjengelighet

Status: [ ] Planlagt

Mål:
- mer konsekvent design
- bedre navigasjon
- forbedret dark mode
- bedre nettbrettstøtte
- bedre tilgjengelighet
- gjennomgang av tekststørrelse, kontrast og trykkflater
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
- Play Store metadata
- screenshots
- release notes
- Data Safety-kontroll
- automatisert eller forenklet Play-release

Ferdig når:
- [ ] intern test er bestått
- [ ] lukket testspor fungerer
- [ ] AAB kan distribueres uten manuelle feil
- [ ] Play Store-listing er klar for produksjon

---

## v2.0 – KOVA Companion 2

Status: [ ] Fremtid

Retning:
- større arkitekturgjennomgang
- mer robust Bridge/backend
- sanntidssynk der KOVA tillater det
- ny personlig startside
- bedre skalerbarhet for flere korps
- grunnlag for web og eventuelt iOS
- videre utvikling basert på erfaring fra hele 1.x-serien

v2.0 skal ikke startes før 1.x-serien har bevist stabil drift og de viktigste brukerbehovene er kjent.

---

## Nærmeste prioritering

1. v1.1 Stabilitet og drift
2. Intern Google Play-test
3. v1.2 Mine aktiviteter 2.0
4. v1.3 Varsler 2.0

Store nye funksjoner skal ikke prioriteres foran kritiske feil eller stabilitetsarbeid.
