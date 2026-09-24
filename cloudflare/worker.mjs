// KOVA dispatch: Firebase Spark + Cloudflare Workers. No private Google key.
const ORIGIN = 'https://border55-repo.github.io';
const FIRESTORE = 'https://firestore.googleapis.com/v1/projects/kova-companion/databases/(default)/documents';
const WORKFLOW = 'https://api.github.com/repos/Border55-repo/KOVA-Companion-Android/actions/workflows/kova-bridge.yml';

function reply(status, body, origin) {
  const headers = {'Content-Type':'application/json', 'Cache-Control':'no-store', 'Vary':'Origin'};
  if (origin === ORIGIN) headers['Access-Control-Allow-Origin'] = ORIGIN;
  return new Response(JSON.stringify(body), {status, headers});
}

async function smallJson(request) {
  const reader = request.body?.getReader();
  if (!reader) throw new Error('body');
  const chunks = [];
  let size = 0;
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    size += value.length;
    if (size > 1024) { await reader.cancel(); throw new Error('body'); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  return JSON.parse(new TextDecoder().decode(bytes));
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get('Origin');
    const path = new URL(request.url).pathname;
    const respond = (status, body) => reply(status, body, origin);
    if (origin && origin !== ORIGIN) return respond(403, {error:'origin_not_allowed'});
    if (request.method === 'GET' && (path === '/' || path === '/health')) {
      return respond(200, {service:'kova-dispatch', version:1, configured:!!env.GITHUB_TOKEN});
    }
    if (!['/dispatch', '/verify'].includes(path)) return respond(404, {error:'not_found'});
    if (request.method === 'OPTIONS') {
      if (origin !== ORIGIN) return respond(403, {error:'origin_not_allowed'});
      return new Response(null, {status:204, headers:{
        'Access-Control-Allow-Origin':ORIGIN,
        'Access-Control-Allow-Methods':'POST, OPTIONS',
        'Access-Control-Allow-Headers':'Authorization, Content-Type',
        'Access-Control-Max-Age':'600', 'Vary':'Origin'
      }});
    }
    if (request.method !== 'POST') return respond(405, {error:'method_not_allowed'});
    const authorization = request.headers.get('Authorization') || '';
    if (!/^Bearer [A-Za-z0-9._-]+$/.test(authorization) || authorization.length > 8192) {
      return respond(401, {error:'sign_in_required'});
    }
    let body;
    try { body = await smallJson(request); } catch { return respond(400, {error:'invalid_request'}); }
    const verify = path === '/verify';
    if (!body || typeof body !== 'object' || Array.isArray(body)) return respond(400, {error:'invalid_request'});
    if (!verify && (!['announcement', 'bridgeSync'].includes(body.command)
      || typeof body.requestId !== 'string' || !/^[A-Za-z0-9._:-]{8,128}$/.test(body.requestId))) {
      return respond(400, {error:'invalid_request'});
    }
    try {
      // Firestore verifies the ID token and enforces the existing adminReady rule.
      // Never replace this with a public document read or an unverified JWT decode.
      const document = verify ? 'adminRuntime/bridge' : `adminCommands/${body.command}`;
      const result = await fetch(`${FIRESTORE}/${document}`, {
        headers:{Authorization:authorization}, redirect:'error', signal:AbortSignal.timeout(10000)
      });
      if ([401,403].includes(result.status)) return respond(403, {error:'admin_required'});
      if (result.status === 404) return respond(409, {error:'command_not_found'});
      if (!result.ok) return respond(502, {error:'firebase_unavailable'});
      if (!verify) {
        const fields = (await result.json()).fields || {};
        if (fields.requestId?.stringValue !== body.requestId || fields.action?.stringValue !== body.command) {
          return respond(409, {error:'command_changed'});
        }
        const status = fields.status?.stringValue;
        if (['running','completed'].includes(status)) return respond(200, {status:'already_processing'});
        if (status !== 'requested') return respond(409, {error:'command_not_pending'});
      }
      if (!env.GITHUB_TOKEN) return respond(503, {error:'github_token_missing'});
      const github = await fetch(WORKFLOW + (verify ? '' : '/dispatches'), {
        method:verify ? 'GET' : 'POST', redirect:'error', signal:AbortSignal.timeout(15000),
        headers:{
          Authorization:`Bearer ${env.GITHUB_TOKEN}`, Accept:'application/vnd.github+json',
          'User-Agent':'KOVA-Cloudflare-Dispatch', 'X-GitHub-Api-Version':'2022-11-28',
          'Content-Type':'application/json'
        },
        ...(verify ? {} : {body:JSON.stringify({ref:'main', inputs:{reason:`admin:${body.command}:${body.requestId}`}})})
      });
      if (!github.ok) return respond(502, {error:'github_rejected', upstreamStatus:github.status});
      if (verify) {
        const workflow = await github.json();
        if (workflow.state !== 'active') return respond(503, {error:'workflow_disabled'});
        return respond(200, {status:'connected'});
      }
      return respond(202, {status:'queued'}); // Acceptance is not device delivery.
    } catch {
      // Never log/return authorization headers, credentials, or upstream bodies.
      return respond(502, {error:'upstream_unavailable'});
    }
  }
};
