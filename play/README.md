# KOVA Companion – Google Play

Denne mappen er kilden for Play Store-klargjøring av KOVA Companion.

## App

- Pakkenavn: `no.juliannordli.kovacomp`
- Kategori: Productivity
- Standardspråk: Norsk (nb-NO)
- Personvern: https://border55-repo.github.io/KlarX/kova/privacy.html
- Distribusjonsformat: Android App Bundle (AAB)
- Target SDK: API 36

## Testspor

1. `internal` brukes først.
2. Når intern installasjon, oppdatering, push, Mine vakter og offline er kontrollert, kan samme build eller neste build flyttes til `closed`.
3. Produksjon er ikke del av automatisk workflow før 1.x er fysisk kvalitetssikret.

## GitHub Actions

Workflowen `Google Play test release` bygger en signert AAB og kan sende den til `internal` eller `closed`.

Følgende GitHub Secrets må finnes:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`

Google Play-appen må opprettes én gang i Play Console og servicekontoen må få tilgang til appen før Developer API kan laste opp releases.

## Før første Play-opplasting

- kontroller Store listing
- kontroller Data Safety mot faktisk SDK-bruk
- legg inn appikon, feature graphic og ekte Android-skjermbilder
- opprett intern testerliste / Google Group
- gjennomfør App content / content rating
- legg inn personvern-URL
- bekreft at ingen Røde Kors- eller KOVA-tilknytning antydes

Se også:
- `data-safety.md`
- `testing.md`
- `screenshots/README.md`
