# Data Safety-notater

Dette dokumentet er et arbeidsgrunnlag for Google Play Data safety. Endelig skjema må kontrolleres mot versjonen som faktisk lastes opp.

## Appens egen behandling

- Offentlige KOVA-kalenderdata: hentes for appfunksjonalitet.
- Favoritter, filtre, valgt korps og lokal cache: lagres på enheten.
- Ingen Røde Kors-/Okta-innlogging.
- Ingen egen konto eller egen brukerdatabase.
- Ingen annonser.
- Ingen Firebase Analytics i appens avhengigheter.

## Firebase Cloud Messaging

Appen bruker `com.google.firebase:firebase-messaging`. Firebase-dokumentasjonen opplyser at Cloud Messaging automatisk behandler appversjon og Firebase user agent, og har en transitiv avhengighet til Firebase Installations.

BigQuery-eksport av leveringsmålinger er ikke aktivert i KOVA Companion.

## Kontroller før innsending

- sjekk gjeldende Firebase Data Safety-dokumentasjon
- sjekk hvilke data Firebase Installations oppgir
- bekreft at Analytics fortsatt ikke er inkludert
- bekreft at BigQuery delivery metrics fortsatt er avslått
- oppdater erklæringen hvis nye SDK-er eller tjenester legges til
