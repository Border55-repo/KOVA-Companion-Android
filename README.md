# Kova Companion

[Åpne webappen](https://border55-repo.github.io/KOVA-Companion-Android/) ·
[Adminpanel](https://border55-repo.github.io/KOVA-Companion-Android/admin/)

Kova Companion er en webapp for offentlige KOVA-aktiviteter i Røde Kors Hjelpekorps.
Den fungerer i nettleseren på iPhone og Android. Den native Android-appen er
avsluttet; appkoden og byggløypen er fjernet fra prosjektet. Tidligere GitHub
releaser er historikk og skal ikke brukes til nye utgivelser.

## Aktive deler

- `pwa/`: webapp, installering på hjemskjerm, varsler og adminpanel.
- `bridge/`: henter offentlige KOVA-data, oppdaterer Firestore og sender Web Push.
- `.github/workflows/companion-web.yml`: publiserer webappen til GitHub Pages.
- `.github/workflows/kova-bridge.yml`: kjører Bridge regelmessig.
- `.github/workflows/release-notice-push.yml`: publiserer kontrollert endringslogg og PWA-push etter release.

Varsler sendes til PWA-abonnement på begge mobilplattformene. Se
[`PRODUCT_SCOPE.md`](PRODUCT_SCOPE.md), [`ROADMAP.md`](ROADMAP.md),
[`docs/WEB.md`](docs/WEB.md) og [`bridge/RELEASE_NOTICES.md`](bridge/RELEASE_NOTICES.md).

