# KOVA Companion – Roadmap 2.x

KOVA Companion skal utvikles som ett produkt på Android og PWA. Brukerrettede funksjoner regnes ikke som levert før de fungerer og er kontrollert på begge plattformer.

## Faste leveringsregler
- Android og PWA skal ha samme kjernefunksjoner, begreper og brukerflyt.
- Nye funksjoner implementeres på begge plattformer før de annonseres.
- Ingen push om en ny funksjon før både Android-versjonen og PWA-versjonen er live og verifisert.
- Endringslogg publiseres sentralt og skal kunne vises i både Android og PWA.
- Korpsvalg følger 2.0.1-modellen: én liste, Favoritt og Følg. Ingen Hoved Korps/Aktivitetstilknytning.
- Korps uten relevante kommende vakter skjules, men fulgte, favorittmerkede og aktivt valgte korps beholdes.
- Gamle vakter skjules som standard.
- Kritiske feil, manglende vakter, synk og varsling prioriteres foran nye funksjoner.

## v2.0.2 – Synkronisert Android/PWA
Status: Pågår

Mål:
- [x] tilbake til enkel korpsliste
- [x] Favoritt og Følg som separate valg
- [x] skjule inaktive korps i PWA
- [x] samme inaktive-korpsregel lagt inn i Android
- [x] Android versjon bumpet til 2.0.2 / versionCode 37
- [ ] verifisere PWA CI/Pages
- [ ] verifisere Android CI og signert release
- [ ] kontrollere at gamle vakter er skjult likt på begge plattformer
- [ ] fysisk kontroll Android + PWA før brukerannonsering

## v2.1 – Endringslogg & publisering
Status: Under utvikling

Mål:
- [x] publiseringspanel i KOVA Admin
- [x] tittel, endringstekst og valg for push
- [x] Firestore-regler for endringslogg og announcement-kommando
- [ ] deploy og verifiser Firestore-reglene
- [ ] Bridge skal behandle announcement-kommando sikkert og idempotent
- [ ] push skal nå Android FCM og PWA Web Push
- [ ] egen Endringslogg-visning i Android
- [ ] samme Endringslogg-visning i PWA
- [ ] push skal åpne riktig endringslogg
- [ ] publiseringsstatus i Admin: kladd / publisert / push sendt / feil
- [ ] historikk over tidligere publiseringer
- [ ] fysisk ende-til-ende test før funksjonen annonseres

## v2.2 – Varsler 3.0
Mål:
- stabil Følg-basert varsling
- samme varselpreferanser Android/PWA
- tydelig varselhistorikk
- bedre deduplisering
- varsling ved ny, endret og fjernet vakt
- påminnelser med samme grunnmodell på begge plattformer
- adminstatus for pushkø og feil

## v2.3 – Mine vakter & kalender
Mål:
- identisk Mine vakter-opplevelse
- favoritter som tåler KOVA-endringer
- lik påminnelsesmodell
- bedre kalenderintegrasjon
- lokale notater
- rask oversikt over neste favorittvakt

## v2.4 – Drift og datakvalitet
Mål:
- Systemhelse basert på faktisk Bridge-status, ikke misvisende tidsgrenser
- kommende-vakt-telling per korps
- automatisk skjuling/gjenvisning av korps uten vakter
- bedre fallback ved KOVA/Bridge-feil
- automatisk cache/service-worker-refresh
- diagnostikk i Admin, ikke hos vanlige brukere

## v2.5 – Distribusjon
Mål:
- stabil signert GitHub Release
- app-update.json alltid synkronisert
- Google Play intern test
- lukket Play-test
- release notes generert fra publisert endringslogg
- samme publiserte endringsinformasjon i Android og PWA

## Neste arbeidsrekkefølge
1. Få v2.0.2 grønn og live på Android og PWA.
2. Deploy Firestore-reglene som løser «Missing or insufficient permissions».
3. Fullfør Bridge-behandling av announcement-kommando.
4. Bygg endringsloggvisning likt i Android og PWA.
5. Ende-til-ende-test: Admin → endringslogg → push → åpning i Android/PWA.
6. Først deretter annonseres endringsloggfunksjonen til brukerne.

Prosjekteier: Julian Nordli. KOVA Companion er et uavhengig prosjekt og ikke en offisiell Røde Kors-app.
