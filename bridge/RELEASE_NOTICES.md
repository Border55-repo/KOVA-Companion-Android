# Release notices without the Admin panel

For each user-facing release, verify that the new PWA version is live, then update
`bridge/release-notice.json` in a reviewed pull request. Give every notice a new
ID. Merging to `main` runs `release-notice-push.yml`, which publishes the
changelog and sends one announcement to Android and enabled PWA subscriptions.

The workflow uses the existing Firebase service-account secret. It runs the
send job only for a push to `main` by the repository owner. Other people with
repository or Firebase administrative access may still be able to send notices;
this is not a private identity or credential belonging to an AI assistant.

Before sending, the job checks that the version in the manifest is visible on
the live PWA. Firestore `releaseAnnouncements/{id}` records the result and
prevents a completed notice from being sent again if the workflow is rerun.
Failed or interrupted notices require inspection before creating another ID;
do not resend all users automatically after partial delivery.

The delivery counts mean acceptance by push services, not display on a phone.
Check the workflow result and perform a physical device test when a release
changes notification behavior.
