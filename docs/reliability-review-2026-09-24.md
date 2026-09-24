# Kova Companion: reliability review

Scope: admin panel, PWA, Android, and the shared Bridge/notification path.

## Fixed

- Cloudflare fetch redirect mode rejected by workerd: use manual mode and fail
  on non-success responses, without forwarding credentials to redirects.
- Authenticated connection diagnosis without broadcasting a test message.
- PWA corpus codes with spaces or Norwegian letters now resolve the index's
  actual snapshot filename. Concurrent loads for the same corps share a request.
- Duplicate foreground refreshes are coalesced. Admin polling pauses while
  hidden, does not overlap, and recovers after a transient failure.
- Bridge prioritizes the least recently checked corps. Wall-clock buckets alone
  can starve a corps when scheduled jobs are delayed.
- Cloudflare checks the Bridge every five minutes, skips active or very recent
  successful runs, and dispatches the fixed workflow. GitHub cron is a fallback.
- Android does not show stale/queued/error health as green, and a damaged cached
  record no longer prevents startup. Web-only changes do not rebuild Android.

## Why Bridge remains

Bridge normalizes public schedules once for both apps, compares changes, sends
notifications and reminders, and supplies offline snapshots. A manual full sync
is a recovery tool; users should not need it in daily use. Cloudflare improves
startup reliability but GitHub runner queues can still delay execution.

## Demonstration boundaries

This is an independent prototype, not an official Red Cross service. It uses
public schedule pages, not an official API. A future authorized API can replace
the Bridge data collector while keeping the app's normalized event contract.
No private credentials should be requested or exposed during the demonstration.

Automated tests cover notification filtering, offline fallback, dispatch,
duplicate prevention, corpus routing, polling fairness and Android resilience.
Push-service acceptance does not prove a notification was displayed on a device.
Verify one physical iPhone Home Screen installation and one Android device before
demonstrating delivery or making claims about timing.

Operational dependency: the scoped GitHub token expires 23 December 2026 and must
be renewed before then. No new paid service or Firebase billing upgrade was added.
