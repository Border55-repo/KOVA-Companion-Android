# KOVA Cloudflare dispatcher (Firebase Spark)

The worker wakes the existing `kova-bridge.yml` GitHub workflow after an admin command has been saved in Firestore. GitHub queue time remains possible; success means accepted by GitHub, not delivery to a phone. Existing scheduled runs and favorite-corps filtering remain in place. No Firebase Functions deployment or Android release is required.

## Cloudflare configuration

- Worker: `kova-dispatch`, URL: `https://kova-dispatch.juliannordli.workers.dev`.
- Paste `worker.mjs` into the dashboard's `worker.js` editor and Deploy, or deploy with Wrangler from this directory.
- Add **Secret** `GITHUB_TOKEN` in Settings → Variables and Secrets. Use a fine-grained GitHub token restricted to `Border55-repo/KOVA-Companion-Android`, with repository **Actions: Read and write** and the automatically required metadata read permission. No contents write permission is required. Choose an expiration and replace the secret before it expires. Never place the token in code, GitHub Pages, logs, or chat.
- Keep Cloudflare Workers Free and Firebase Spark. No paid resources or billing changes are part of this setup.

## Access checks

`POST /dispatch` accepts `{ "command": "announcement", "requestId": "..." }` (also supports `bridgeSync`) and a Firebase ID token in `Authorization: Bearer ...`.

The worker reads the matching protected `adminCommands` document using that same token. Firestore verifies the token and applies existing `adminReady()` rules (superuser identity and completed initial password change). It rejects missing, failed, replaced, and unauthorized commands. It never accepts notification text, repository, ref, or workflow from the caller. Already running/completed commands do not dispatch again. Concurrent retries while a command is still requested may queue extra GitHub runs; GitHub serializes Bridge runs and the existing backend claims each announcement document before delivery.

Allowed browser origin: `https://border55-repo.github.io`. CORS supplements authentication; it is not the access-control boundary. Credentials are sent only to the fixed Firebase/GitHub endpoints and redirects are rejected. Firebase service-account and VAPID keys stay in the existing backend.

`GET /health` reports whether a secret is configured, not whether it works. Authenticated `POST /verify` with `{}` checks the protected admin runtime document and reads the active GitHub workflow without dispatching. A successful verify confirms read access; a real authorized dispatch is still required to verify Actions write permission.

## Validation

Run `node --test cloudflare/worker.test.mjs` from the repository root. Admin integration lives in KlarX `apps/kova/admin/dispatch.js`; its request is made only after the Firestore write succeeds. Network/credential failure leaves the saved request available to the scheduled Bridge run and displays that fallback explicitly.

After saving the secret, check `/health`, then publish an intended announcement from KOVA Admin. Confirm the workflow starts, the admin delivery status updates, and a physical iPhone/Android receives the notification. Do not use an unauthenticated public dispatch endpoint or broadcast a test without the owner's request.

References: [Firestore REST authentication](https://firebase.google.com/docs/firestore/use-rest-api), [GitHub workflow dispatch permissions](https://docs.github.com/en/rest/actions/workflows#create-a-workflow-dispatch-event), [Cloudflare secrets](https://developers.cloudflare.com/workers/configuration/secrets/).
