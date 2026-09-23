# Data Safety – arbeidsgrunnlag

Dette dokumentet er et kontrollgrunnlag for skjemaet i Google Play Console. Endelig innsending må kontrolleres mot Play Consoles gjeldende definisjoner.

## Appens egen funksjonalitet

KOVA Companion:
- oppretter ikke brukerkonto
- ber ikke om navn, e-post, telefonnummer eller Røde Kors-/Okta-legitimasjon
- ber ikke om posisjon
- ber ikke om kontakter, bilder, mikrofon, kamera, helse- eller betalingsdata
- bruker ikke annonser
- inkluderer ikke Firebase Analytics
- inkluderer ikke Crashlytics
- eksporterer ikke FCM delivery metrics til BigQuery

Favoritter, notater, stille perioder og lokal varselhistorikk lagres lokalt på Android-enheten.

## Firebase Cloud Messaging

Android-appen inkluderer `com.google.firebase:firebase-messaging`.

Firebase dokumenterer at Cloud Messaging samler appversjon og Firebase user agent, og at Firebase Installations genererer/samler en Firebase Installation ID (FID) som brukes av FCM for meldingslevering.

Arbeidshypotese for Play-skjemaet:
- Data type: Device or other IDs / app installation identifier
- Formål: App functionality (push notifications)
- Behandling: automatisk av Firebase SDK
- Kryptering under overføring: ja
- Ikke brukt til annonsering
- Ikke solgt
- Deling må vurderes etter Play-definisjonen; Firebase beskriver subprocessors/service providers som del av tjenesteleveransen.

## Nettverk

Appen kobler til:
- offentlig KOVA-side
- KOVA Companion Bridge på GitHub
- Firebase Cloud Messaging
- GitHub Releases for oppdateringssjekk

## Personvern-URL

https://border55-repo.github.io/KlarX/kova/privacy.html

## Kontroll før Play-innsending

- kontroller siste Firebase Data Safety-dokumentasjon
- kontroller transitive Firebase SDK-er med Gradle dependency report
- kontroller at Analytics/Crashlytics fortsatt ikke er lagt inn
- kontroller AndroidManifest for nye permissions
- oppdater dette dokumentet ved nye SDK-er eller backendfunksjoner
