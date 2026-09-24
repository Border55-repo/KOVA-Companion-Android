// KOVA dispatch gateway. Paste this entire file into Cloudflare's worker.js.
// Store GITHUB_TOKEN as a Cloudflare Secret, never in this file.
const ORIGIN = "https://border55-repo.github.io";
const FIRESTORE = "https://firestore.googleapis.com/v1/projects/kova-companion/databases/(default)/documents";
const WORKFLOW = "https://api.github.com/repos/Border55-repo/KOVA-Companion-Android/actions/workflows/kova-bridge.yml";
const headers = {
  "Access-Control-Allow-Origin": ORIGIN,
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
  "Cache-Control": "no-store",
  "Vary": "Origin",
};
const reply = (status, body) => Response.json(body, { status, headers });
const field = (doc, name) => doc.fields?.[name]?.stringValue;

async function github(env, path, method = "GET", body) {
  if (!env.GITHUB_TOKEN) throw new Error("missing_secret");
  return fetch(WORKFLOW + path, {
    method,
    headers: {
      Authorization: `Bearer ${env.GITHUB_TOKEN}`,
      Accept: "application/vnd.github+json",
      "Content-Type": "application/json",
      "User-Agent": "KOVA-Free-Dispatcher",
      "X-GitHub-Api-Version": "2022-11-28",
    },
    body: body ? JSON.stringify(body) : undefined,
    redirect: "error",
    signal: AbortSignal.timeout(15000),
  });
}

async function dispatch(env, reason) {
  const result = await github(env, "/dispatches", "POST", {
    ref: "main", inputs: { reason },
  });
  if (!result.ok) throw new Error("github_dispatch_failed");
}

async function smallJson(request) {
  const reader = request.body?.getReader();
  if (!reader) throw new Error("invalid_body");
  let text = "", size = 0;
  const decoder = new TextDecoder();
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > 2048) { await reader.cancel(); throw new Error("invalid_body"); }
      text += decoder.decode(value, { stream: true });
    }
    return JSON.parse(text + decoder.decode());
  } finally { reader.releaseLock(); }
}

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (request.method === "GET" && (path === "/" || path === "/health")) {
      return reply(200, { service: "kova-dispatch", configured: Boolean(env.GITHUB_TOKEN) });
    }
    if (path !== "/dispatch") return reply(404, { error: "not_found" });
    if (request.headers.get("Origin") !== ORIGIN) return reply(403, { error: "origin_denied" });
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    if (request.method !== "POST") return reply(405, { error: "method_not_allowed" });
    const auth = request.headers.get("Authorization") || "";
    if (!/^Bearer [A-Za-z0-9._-]{20,8192}$/.test(auth)) return reply(401, { error: "sign_in_required" });
    let command, requestId;
    try {
      ({ command, requestId } = await smallJson(request));
      if (!["announcement", "bridgeSync"].includes(command) ||
          typeof requestId !== "string" || !/^[A-Za-z0-9_-]{8,128}$/.test(requestId)) throw new Error();
    } catch { return reply(400, { error: "invalid_request" }); }
    if (!env.GITHUB_TOKEN) return reply(503, { error: "not_configured" });
    try {
      // Firebase verifies the ID token and applies deployed adminReady() rules.
      // The Worker has no service account and cannot bypass those rules.
      const result = await fetch(`${FIRESTORE}/adminCommands/${command}`, {
        headers: { Authorization: auth }, redirect: "error", signal: AbortSignal.timeout(10000),
      });
      if (result.status === 401 || result.status === 403) return reply(403, { error: "admin_required" });
      if (result.status === 404) return reply(409, { error: "command_missing" });
      if (!result.ok) return reply(502, { error: "firebase_unavailable" });
      const doc = await result.json();
      if (field(doc, "status") !== "requested" || field(doc, "requestId") !== requestId ||
          field(doc, "action") !== command || field(doc, "requestedBy") !== "superuser") {
        return reply(409, { error: "command_changed" });
      }
      if (command === "announcement" && (field(doc, "source") !== "changelog" ||
          field(doc, "topic") !== "kova_all_users")) return reply(409, { error: "invalid_command" });
      await dispatch(env, `admin-${command}`);
      // Accepted by GitHub, not evidence of push delivery. Admin follows Firestore status.
      return reply(202, { status: "queued" });
    } catch {
      return reply(502, { error: "dispatch_unavailable" });
    }
  },
  async scheduled(_event, env) {
    // Configure */5 * * * * in Cloudflare. Independent of GitHub's cron scheduler.
    // Skip when a recent/running job already covers this interval.
    const result = await github(env, "/runs?branch=main&per_page=10");
    if (!result.ok) throw new Error("github_status_failed");
    const { workflow_runs: runs } = await result.json();
    if (!Array.isArray(runs)) throw new Error("github_status_invalid");
    if (runs.some(run => run.status !== "completed" ||
        Date.now() - Date.parse(run.created_at) < 240000)) return;
    await dispatch(env, "cloudflare-schedule");
  },
};
