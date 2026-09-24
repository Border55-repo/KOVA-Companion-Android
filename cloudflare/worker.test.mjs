import { test, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.mjs';
const original = globalThis.fetch;
afterEach(() => { globalThis.fetch = original; });
const env = { GITHUB_TOKEN: 'test-secret' };
const id = '2026-09-24T19-24-23-440Z';
const token = 'Bearer ' + 'a'.repeat(30);
function request(body = {command:'announcement', requestId:id}, overrides = {}) {
  return new Request('https://example.workers.dev/dispatch', {method:'POST',headers:{Origin:'https://border55-repo.github.io',Authorization:token},body:JSON.stringify(body),...overrides});
}
function document(overrides = {}) {
  return {fields:Object.fromEntries(Object.entries({status:'requested',requestId:id,action:'announcement',requestedBy:'superuser',source:'changelog',topic:'kova_all_users',...overrides}).map(([k,v])=>[k,{stringValue:v}]))};
}
test('rejects unauthorized origin before any upstream request', async () => {
  globalThis.fetch = () => { throw Error('must not call'); };
  assert.equal((await worker.fetch(request(undefined,{headers:{Origin:'https://evil.example',Authorization:token}}),env)).status,403);
});
test('requires a bearer token', async () => {
  assert.equal((await worker.fetch(request(undefined,{headers:{Origin:'https://border55-repo.github.io'}}),env)).status,401);
});
test('rejects unknown commands and oversized bodies', async () => {
  assert.equal((await worker.fetch(request({command:'arbitrary',requestId:id}),env)).status,400);
  assert.equal((await worker.fetch(request({command:'announcement',requestId:'x'.repeat(3000)}),env)).status,400);
});
test('Firebase rules reject a non-admin, without invoking GitHub', async () => {
  let calls=0;
  globalThis.fetch=async(url,init)=>{calls++;assert.ok(url.startsWith('https://firestore.googleapis.com/'));assert.equal(init.headers.Authorization,token);return new Response('',{status:403});};
  assert.equal((await worker.fetch(request(),env)).status,403);assert.equal(calls,1);
});
test('stale or completed commands do not dispatch', async () => {
  for(const changes of [{status:'completed'},{requestId:'new-request'},{topic:'wrong'},{source:'wrong'}]) {
    let calls=0;globalThis.fetch=async()=>{calls++;return Response.json(document(changes));};
    assert.equal((await worker.fetch(request(),env)).status,409);assert.equal(calls,1);
  }
});
test('authorized matching command starts only fixed workflow on main', async () => {
  const calls=[];globalThis.fetch=async(url,init)=>{calls.push({url,init});return calls.length===1?Response.json(document()):new Response(null,{status:204});};
  const result=await worker.fetch(request(),env);assert.equal(result.status,202);
  assert.equal(calls[1].url,'https://api.github.com/repos/Border55-repo/KOVA-Companion-Android/actions/workflows/kova-bridge.yml/dispatches');
  assert.deepEqual(JSON.parse(calls[1].init.body),{ref:'main',inputs:{reason:'admin-announcement'}});
  assert.equal(calls[1].init.headers.Authorization,'Bearer test-secret');
  assert.deepEqual(await result.json(),{status:'queued'});
});
test('upstream failure is not reported as delivery and leaks no credentials', async()=>{
  let count=0;globalThis.fetch=async()=>++count===1?Response.json(document()):new Response('test-secret',{status:403});
  const r=await worker.fetch(request(),env);assert.equal(r.status,502);assert.ok(!(await r.text()).includes('test-secret'));
});
test('missing secret reports not configured',async()=>{
  assert.equal((await worker.fetch(request(),{})).status,503);
});
test('scheduled event skips active runs and starts when idle',async()=>{
  for(const active of [true,false]) {
    let calls=0;globalThis.fetch=async()=>++calls===1?Response.json({workflow_runs:[{status:active?'in_progress':'completed',created_at:'2020-01-01T00:00:00Z'}]}):new Response(null,{status:204});
    await worker.scheduled({},env);assert.equal(calls,active?1:2);
  }
});
