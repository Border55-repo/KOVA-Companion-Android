# Kova Companion

Kova Companion is an independent product. The Android app, web app, admin panel,
Bridge and Cloudflare dispatcher belong to this repository. URKH is a separate
product and has no deployment dependency on this repository.

- Web: https://border55-repo.github.io/KOVA-Companion-Android/
- Admin: https://border55-repo.github.io/KOVA-Companion-Android/admin/
- Source: `pwa/`, with its own checks and Pages deployment.
- Local checks: `node --test pwa/tests/*.test.mjs` from the repository root.

Firebase accounts, Firestore data, favorite filtering and Cloudflare credentials
are unchanged. Both Pages paths use the same origin, so dispatcher CORS continues
to work. Do not put GitHub tokens in web files.

The old KlarX paths only provide migration links. An installed web app may need
to be added to the Home Screen again from the new address, with notifications
enabled there. Browser-local preferences remain origin-scoped; separate Home
Screen storage on iOS may require selecting favorites again. Existing Android
releases can use the old admin redirect without an APK update. The new Android
name and direct admin link ship with the next Android release.
