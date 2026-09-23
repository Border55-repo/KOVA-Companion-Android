# KOVA Companion – v1.0 release candidate test

Denne sjekklisten brukes før v1.0.0 publiseres.

## Oppgradering

- Installer siste stabile versjon.
- Kjør «Sjekk etter oppdatering» i appen.
- Bekreft at nyere versjon oppdages uten HTTP 403.
- Trykk «Last ned oppdatering».
- Bekreft at Android DownloadManager starter nedlastingen uten å åpne direkte APK-lenke i Chrome.
- Installer oppdateringen og bekreft at appdata, favoritter og innstillinger beholdes.

## Førstegangsoppsett

- Installer appen rent på en testtelefon.
- Bekreft ikon og splash.
- Fullfør onboarding.
- Gi eller avslå varslingstillatelse og kontroller at Appstatus viser riktig resultat.
- Bekreft at snarveien til Androids varselinnstillinger fungerer ved avslåtte varsler.

## KOVA-data

- [x] Synk Ullensaker.
- [x] Sammenlign antall og synlige aktiviteter med KOVA (bekreftet manuelt av prosjektansvarlig mot innlogget KOVA).
- Kontroller aktivitet med vanlig klokkeslett.
- Kontroller aktivitet uten klokkeslett.
- Kontroller KOVA-tid med `-> HH:mm` og at den vises som «Til HH:mm».
- Kontroller direkte KOVA-fallback dersom Bridge ikke kan brukes.

## Visninger og søk

- Listevisning.
- Ukevisning.
- Månedsvisning.
- Søk på aktivitetsnavn.
- Søk på aktivitetstype.
- Filtrer aktivitetstype.
- Søk og bytt hjelpekorps.

## Varsler

- Ny aktivitet.
- Endret aktivitet.
- Fjernet aktivitet.
- Trykk på varselet og bekreft at riktig aktivitet åpnes.
- Kontroller at samme endring ikke gir duplikatvarsel.
- Test varsling fra minst to fulgte hjelpekorps.

## Mine aktiviteter

- Legg til favoritt.
- Bekreft at den vises i Mine aktiviteter.
- Test Alle / 7 dager / 30 dager.
- Test 24t- og 2t-påminnelse.
- Endre dato/tid og kontroller at favoritten følger aktiviteten.
- Fjern favoritt.

## Kalender

- Legg vanlig aktivitet i kalender.
- Legg aktivitet uten klokkeslett i kalender som heldagsaktivitet.
- Kontroller tittel, dato og KOVA-lenke.

## Release

- [x] Android unit tests grønne.
- [x] Bridge unit tests grønne.
- [x] Signert APK verifisert med apksigner.
- [x] AAB bygget.
- [x] SHA256SUMS publisert.
- [x] app-update.json peker på riktig release.
- [x] Personvernerklæring og Data Safety-notater kontrollert.
- Intern Google Play-test gjennomført før v1.0.0.
