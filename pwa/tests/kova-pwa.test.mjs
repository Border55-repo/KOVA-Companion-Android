import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { spawnSync } from "node:child_process";

test("KOVA PWA JavaScript has valid syntax", () => {
  const result = spawnSync(process.execPath, ["--check", "pwa/app.js"], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test("KOVA PWA manifest is installable and scoped", async () => {
  const manifest = JSON.parse(await readFile("pwa/manifest.webmanifest", "utf8"));
  assert.equal(manifest.name, "Kova Companion");
  assert.equal(manifest.display, "standalone");
  assert.equal(manifest.start_url, "./");
  assert.equal(manifest.scope, "./");
  assert.ok(Array.isArray(manifest.icons) && manifest.icons.length > 0);
});

test("KOVA PWA contains core mobile actions", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  for (const text of ["Fulgte", "Legg i kalender", "Del aktivitet", "Logg inn / Åpne KOVA"]) {
    assert.ok(html.includes(text), "Missing UI text: " + text);
  }
});


test("KOVA PWA auto-repairs an existing push subscription on startup", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(app.includes("async function repairPushRegistration"));
  assert.ok(app.includes("await repairPushRegistration();"));
});


test("KOVA PWA uses the official Firebase Web SDK for subscription storage", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(app.includes("firebase-firestore.js"));
  assert.ok(app.includes("firebase.setDoc"));
  assert.ok(!app.includes("documents:commit"));
});


test("KOVA PWA repairs push when returning to foreground", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(app.includes('window.addEventListener("focus",refreshOnForeground)'));
  assert.ok(app.includes('registration.update()'));
  assert.ok(app.includes('controllerchange'));
});


test("KOVA PWA cache-busts fresh KOVA data requests", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(app.includes("ts=1790163677805") || app.includes("Date.now()"));
  assert.ok(app.includes('cache:"no-store"'));
});


test("KOVA PWA shows project owner information", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  assert.ok(html.includes("Prosjekteier: Julian Nordli"));
  assert.ok(html.includes("ikke en offisiell Røde Kors-app"));
});


test("KOVA PWA has an everyday dashboard for upcoming shifts", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  for (const text of ["Neste vakt", "Denne uka", "Senere", "Mine vakter"]) {
    assert.ok(html.includes(text), "Missing everyday UI text: " + text);
  }
  assert.ok(app.includes("renderEverydayDashboard"));
  assert.ok(app.includes("5*60*1000"));
});


test("KOVA PWA supports remote admin cache generation", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes('href="./admin/"'));
  assert.ok(app.includes("checkRemoteCacheEpoch"));
  assert.ok(app.includes('"publicConfig","pwa"'));
  assert.ok(app.includes("caches.keys()"));
});


test("KOVA Admin JavaScript has valid syntax", () => {
  const result = spawnSync(process.execPath, ["--check", "pwa/admin/app.js"], { encoding: "utf8" });
  assert.equal(result.status, 0, result.stderr || result.stdout);
});

test("KOVA Admin contains secure first-login and cache controls", async () => {
  const html = await readFile("pwa/admin/index.html", "utf8");
  const app = await readFile("pwa/admin/app.js", "utf8");
  assert.ok(html.includes("Superuser"));
  assert.ok(html.includes("Bytt midlertidig passord"));
  assert.ok(html.includes("Publiser ny PWA-cache"));
  assert.ok(app.includes("updatePassword"));
  assert.ok(app.includes("mustChangePassword"));
  assert.ok(app.includes('"publicConfig","pwa"'));
});


test("KOVA Admin has live health and Bridge sync request", async () => {
  const html = await readFile("pwa/admin/index.html", "utf8");
  const app = await readFile("pwa/admin/app.js", "utf8");
  assert.ok(html.includes("Systemhelse"));
  assert.ok(html.includes("Be om Bridge-synk"));
  assert.ok(app.includes('"adminRuntime","bridge"'));
  assert.ok(app.includes('"adminCommands","bridgeSync"'));
  assert.ok(app.includes("organizationChecks"));
  assert.ok(app.includes("age>35"));
});


test("KOVA PWA keeps a network-first offline fallback for Bridge data", async () => {
  const sw = await readFile("pwa/sw.js", "utf8");
  assert.ok(sw.includes('url.hostname==="raw.githubusercontent.com"'));
  assert.ok(sw.includes('/bridge/data/'));
  assert.ok(sw.includes('fetch(event.request)'));
  assert.ok(sw.includes('searchParams.delete("ts")'));
  assert.ok(sw.includes('cache.put(cacheKey'));
  assert.ok(sw.includes('caches.match(cacheKey)'));
});

test("KOVA PWA calendar export builds a valid local ICS event", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(app.includes('BEGIN:VCALENDAR'));
  assert.ok(app.includes('BEGIN:VEVENT'));
  assert.ok(app.includes('DTSTART'));
  assert.ok(app.includes('DTEND'));
  assert.ok(app.includes('SUMMARY:'));
  assert.ok(app.includes('text/calendar'));
  assert.ok(app.includes('navigator.canShare'));
});

test("KOVA Admin live Bridge sync status auto-refreshes while running", async () => {
  const app = await readFile("pwa/admin/app.js", "utf8");
  assert.ok(app.includes('bridgeCommandView'));
  assert.ok(app.includes('scheduleDashboardRefresh'));
  assert.ok(app.includes('active?10000:60000'));
  assert.ok(app.includes('command?.status'));
});


test("KOVA PWA Mine vakter 2.0 favorite dashboard is present", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes('id="myShiftsCard"'));
  assert.ok(html.includes('Neste valgte vakt'));
  assert.ok(app.includes("refreshFavoriteDashboard"));
  assert.ok(app.includes("loadFavoriteEvents"));
});

test("KOVA PWA stores local notes per event", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes('id="detailNote"'));
  assert.ok(html.includes("Lagres bare på denne enheten"));
  assert.ok(app.includes('"kova.pwa.notes"'));
  assert.ok(app.includes("saveNote"));
  assert.ok(app.includes("noteFor"));
});


test("KOVA PWA Mine vakter reminders use Bridge-backed Web Push", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes('id="reminderSelect"'));
  assert.ok(app.includes('"webPushReminders"'));
  assert.ok(app.includes("syncReminderBackend"));
  assert.ok(app.includes("reminderToken"));
  assert.ok(app.includes("syncAllReminders"));
});

test("KOVA PWA relinks favorites when KOVA changes event ids", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(app.includes("favoriteMeta"));
  assert.ok(app.includes("reconcileFavorites"));
  assert.ok(app.includes("migrateFavorite"));
  assert.ok(app.includes("semanticKey"));
});

test("KOVA PWA scales Mine vakter with paging and month groups", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes('id="loadMoreBtn"'));
  assert.ok(app.includes("displayLimit"));
  assert.ok(app.includes("event-group"));
  assert.ok(app.includes("state.displayLimit+=20"));
});

test("KOVA PWA includes selected reminder in calendar export", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(app.includes("BEGIN:VALARM"));
  assert.ok(app.includes("TRIGGER:-PT"));
  assert.ok(app.includes("reminderMinutesFor(event)"));
});


test("KOVA PWA Varsler 2.0 preferences and history are wired", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  const sw = await readFile("pwa/sw.js", "utf8");
  assert.ok(html.includes('id="notificationSettings"'));
  assert.ok(html.includes('id="quietEnabled"'));
  assert.ok(app.includes("notificationKinds"));
  assert.ok(app.includes("disabledNotificationTypes"));
  assert.ok(app.includes("syncNotificationPreferenceControls"));
  assert.ok(sw.includes("HISTORY_DB"));
  assert.ok(sw.includes("get-notification-history"));
});

test("KOVA PWA supports scalable corps search and favorites", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes('id="orgSearchInput"'));
  assert.ok(html.includes('id="favoriteOrgBtn"'));
  assert.ok(app.includes("favoriteOrgs"));
  assert.ok(app.includes("loadWithConcurrency"));
  assert.ok(app.includes("updateDataQuality"));
});

test("KOVA PWA event details expose optional location contact and change summary", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes('id="detailExtra"'));
  assert.ok(html.includes('id="detailChange"'));
  assert.ok(app.includes("extractLocation"));
  assert.ok(app.includes("extractContact"));
  assert.ok(app.includes("changeSummary"));
});

test("KOVA PWA offline recovery queues refresh and navigation has cache fallback", async () => {
  const app = await readFile("pwa/app.js", "utf8");
  const sw = await readFile("pwa/sw.js", "utf8");
  assert.ok(app.includes("kova.pwa.pendingRefresh"));
  assert.ok(app.includes("queuedRefresh"));
  assert.ok(sw.includes('event.request.mode==="navigate"'));
  assert.ok(sw.includes('caches.match(navigationKey)'));
});

test("KOVA PWA accessibility includes skip navigation focus and reduced motion", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const css = await readFile("pwa/styles.css", "utf8");
  assert.ok(html.includes('class="skip-link"'));
  assert.ok(html.includes('aria-label="Hurtignavigasjon"'));
  assert.ok(css.includes(":focus-visible"));
  assert.ok(css.includes("prefers-reduced-motion"));
});


test("KOVA PWA consumes Bridge 2.0 capability contract", async () => {
  const html = await readFile("pwa/index.html", "utf8");
  const app = await readFile("pwa/app.js", "utf8");
  assert.ok(html.includes("Web / iPhone PWA 2.1.2"));
  assert.ok(app.includes("bridgeApiVersion"));
  assert.ok(app.includes("bridgeCapabilities"));
  assert.ok(app.includes("indexResult.value.apiVersion"));
});
