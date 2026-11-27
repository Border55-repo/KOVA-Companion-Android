# Direkte utsending fra KOVA Admin – klargjort, ikke aktivert

## Hva endringen gjør

`bridge/main.py` reagerer på en ny `requested`-melding i `adminCommands/announcement`. Den kaller den samme testede utsendingskoden som Bridge, uten å vente på GitHub cron. Oppdateringer til running/completed/failed starter ikke nye utsendinger. Dokumentversjonene i worker-koden beskytter mot at både funksjonen og Bridge tar samme jobb samtidig.

Ingen endring kreves i adminpanelet eller Android. Den eksisterende Bridge-jobben beholdes som reserve. Mislykkede transporter vises fortsatt som failed; den direkte triggeren gir ikke en garanti for fysisk visning på en telefon.

Funksjonen har null minimumsinstanser, én maksimal instans og 540 sekunders tidsgrense. Den bruker en tilordnet runtime-identitet, ikke en privat nøkkel i nettappen eller i kildekoden.

## Verifisert tilgangsbegrensning

24.09.2026 viste prosjektets testIamPermissions ingen av de kontrollerte rettighetene for funksjonsoppretting/-oppdatering, Cloud Build, API-aktivering eller service account actAs. Kontroll av billingInfo svarte HTTP 403. Betalingsstatus er derfor ukjent; det er ikke dokumentert at prosjektet er på gratisplan.

Google krever Blaze-plan for produksjonsutrulling av Firebase Functions. Planen er forbruksbasert. Rettigheter og eventuell aktivering av fakturering må avklares av prosjekteier før utrulling.

- [Firebase: Kom i gang og krav til Blaze](https://firebase.google.com/docs/functions/get-started)
- [Firebase: Firestore-triggere](https://firebase.google.com/docs/functions/firestore-events)

## Utrulling med egen deploy-identitet

1. Prosjekteier bekrefter betalingsplan og godkjenner eventuell forbruksbasert fakturering i Firebase-prosjektet `kova-companion`.
2. Bruk en separat deploy-identitet med nødvendige deployrettigheter. Den må også kunne bruke den eksisterende runtime-servicekontoen. Ikke utvid nettappens tilgang eller legg en GitHub-/Google-nøkkel i PWA-en.
3. Legg deploy-identitetens legitimasjon i GitHub-secret `FIREBASE_DEPLOY_SERVICE_ACCOUNT_JSON`. Den eksisterende `FIREBASE_SERVICE_ACCOUNT_JSON` brukes bare til å finne den allerede fungerende runtime-identiteten.
4. Etter at denne PR-en er flettet til main, kjør workflowen **Deploy direct admin dispatcher**. Den tester koden og deployer bare funksjons-codebase `kova-admin`. Workflowen endrer ikke betalingsplan eller IAM-roller.
5. Publiser én avtalt test fra det vanlige adminpanelet. Bekreft at status går fra requested til running/completed uten en ny GitHub Bridge-kjøring, og sjekk fysisk varsel.

Det nye deploy-secretet er ikke konfigurert av denne endringen. Oppgi aldri private nøkler i chat eller i et repo.

## Validering

39 backendtester bestod lokalt, inkludert eventfiltrering, runtime-legitimasjon, versjonsbasert jobbkrav og utsending til begge plattformer. iPhone-mottak ble bekreftet av brukeren etter manuell Bridge-kjøring; den nye direkte funksjonen er ikke utrullet eller testet i produksjon.
