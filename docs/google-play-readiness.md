# Google Play-klargjøring

## Teknisk status

- App-ID: `no.juliannordli.kovacomp`
- Minimum Android: API 26
- Mål: API 36 for Google Play-innsending i 2026
- Distribusjonsformat: signert Android App Bundle (AAB)
- Release-signering: GitHub Actions med eksisterende release-nøkkel
- Push: Firebase Cloud Messaging
- Ingen brukerinnlogging kreves

## Anbefalt testløp

1. Intern testing først.
2. Installer fra Google Play på minst én fysisk Android-enhet.
3. Verifiser onboarding, varslingssamtykke, korpsvalg og synk.
4. Verifiser FCM-varsler for lagt til, endret og fjernet aktivitet.
5. Verifiser favoritter og 24t/2t-påminnelser.
6. Verifiser søk og Liste/Uke/Måned.
7. Test oppdatering mellom to Play-builds.
8. Gå videre til lukket testing når grunnfunksjonene er stabile.

Google Play tillater opptil 100 testere i intern testing. For nyere personlige utviklerkontoer kan egne krav til lukket testing gjelde før produksjonstilgang.

## Før Play-opplasting

- bruk samme applikasjons-ID videre
- øk alltid `versionCode`
- behold signeringsnøkkelen
- bygg AAB med targetSdk som tilfredsstiller gjeldende Play-krav
- legg inn personvernerklæring
- fyll ut App content og Data safety i Play Console
- oppgi at appen er uoffisiell og bruker offentlig KOVA-data
- kontroller ikon, appnavn, screenshots og butikktekst
