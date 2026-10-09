# Kova Companion Admin

[Åpne adminpanelet](https://border55-repo.github.io/KOVA-Companion-Android/admin/)

Adminpanelet viser Bridge-status, datakontroll per hjelpekorps og aktive
PWA-abonnement. Driftsverktøyene kan be om full synk, oppdatere PWA-cache og
sende en målrettet test til én registrert PWA-enhet.

Adminmeldinger og releasevarsler sendes som Web Push til PWA-brukere på
iPhone og Android. De sendes ikke til den avsluttede native Android-appen.
Releasevarsler kan også publiseres via den gjennomgåtte GitHub Action-løypen
beskrevet i [RELEASE_NOTICES.md](../bridge/RELEASE_NOTICES.md).

Akseptert av push-tjenesten betyr ikke at varselet ble vist på telefonen.

