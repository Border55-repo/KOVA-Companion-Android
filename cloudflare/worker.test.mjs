import test from 'node:test';
import assert from 'node:assert/strict';
import worker from './worker.mjs';
const origin = 'https://border55-repo.github.io';
const requestId = 'test-request-123';
const fields = (status='requested', id=requestId) => ({fields:{
  action:{stringValue:'announcement'}, requestId:{stringValue:id}, status:{stringValue:status}
}});
function request({path='/dispatch', method='POST', headers={}, body={command:'announcement',requestId}}={}) {
  return new Request('https://worker.test'+path, {method,
    headers:{Origin:origin, Authorization:'Bearer firebase-id-token', ...headers},
    ...(['GET','OPTIONS'].includes(method)?{}:{body:JSON.stringify(body)})});
}
function mock(t, responses) {
  const calls=[];
  t.mock.method(globalThis,'fetch',async (url,options)=>{
    calls.push({url,options});
    assert.ok(responses.length,'Unexpected external request');
    const response=responses.shift();
    if(response instanceof Error)throw response;
    return response;
  });
  return calls;
}
test('health reports unconfigured without leaking secrets',async()=>{
  const r=await worker.fetch(request({path:'/health',method:'GET'}),{});
  assert.equal((await r.json()).configured,false);
});
test('allowed preflight succeeds without authentication',async()=>{
  const r=await worker.fetch(request({method:'OPTIONS',headers:{Authorization:''}}),{});
  assert.equal(r.status,204);
  assert.equal(r.headers.get('Access-Control-Allow-Origin'),origin);
});
test('other origins and absent authentication never reach upstreams',async t=>{
  const calls=mock(t,[]);
  assert.equal((await worker.fetch(request({headers:{Origin:'https://evil.test'}}),{})).status,403);
  assert.equal((await worker.fetch(request({headers:{Authorization:''}}),{})).status,401);
  assert.equal(calls.length,0);
});
test('invalid commands, IDs, null and oversized requests are rejected',async t=>{
  mock(t,[]);
  for(const body of [null,{},[],{command:'../publicConfig/pwa',requestId},{command:'announcement',requestId:'bad'},{command:'announcement',requestId,padding:'x'.repeat(1024)}]) {
    assert.equal((await worker.fetch(request({body}),{})).status,400);
  }
});
test('Firebase denies non-admin or expired token before GitHub',async t=>{
  const calls=mock(t,[new Response('{}',{status:403})]);
  assert.equal((await worker.fetch(request(),{GITHUB_TOKEN:'test-secret'})).status,403);
  assert.equal(calls.length,1);
  assert.match(calls[0].url,/\/adminCommands\/announcement$/);
  assert.equal(calls[0].options.headers.Authorization,'Bearer firebase-id-token');
});
test('changed command cannot dispatch',async t=>{
  mock(t,[Response.json(fields('requested','new-request'))]);
  assert.equal((await worker.fetch(request(),{GITHUB_TOKEN:'test-secret'})).status,409);
});
test('running and completed commands do not start a second workflow',async t=>{
  const calls=mock(t,[Response.json(fields('running')),Response.json(fields('completed'))]);
  for(let i=0;i<2;i++)assert.equal((await worker.fetch(request(),{})).status,200);
  assert.equal(calls.length,2);
});
test('missing secret is reported only after authorization',async t=>{
  mock(t,[Response.json(fields())]);
  const r=await worker.fetch(request(),{});
  assert.equal(r.status,503);
  assert.equal((await r.json()).error,'github_token_missing');
});
test('dispatch uses fixed repository, workflow and main; tokens stay separated',async t=>{
  const calls=mock(t,[Response.json(fields()),new Response(null,{status:204})]);
  const r=await worker.fetch(request(),{GITHUB_TOKEN:'test-secret'});
  assert.equal(r.status,202);
  assert.equal(calls[1].url,'https://api.github.com/repos/Border55-repo/KOVA-Companion-Android/actions/workflows/kova-bridge.yml/dispatches');
  assert.equal(calls[1].options.headers.Authorization,'Bearer test-secret');
  assert.equal(calls[1].options.redirect,'error');
  assert.deepEqual(JSON.parse(calls[1].options.body),{ref:'main',inputs:{reason:'admin:announcement:'+requestId}});
  assert.deepEqual(await r.json(),{status:'queued'});
});
test('GitHub errors do not report success or leak upstream body',async t=>{
  mock(t,[Response.json(fields()),new Response('test-secret',{status:403})]);
  const r=await worker.fetch(request(),{GITHUB_TOKEN:'test-secret'});
  assert.equal(r.status,502);
  assert.equal((await r.text()).includes('test-secret'),false);
});
test('network errors remain recoverable and do not leak exception details',async t=>{
  mock(t,[new Error('private details')]);
  const r=await worker.fetch(request(),{});
  assert.equal(r.status,502);
  assert.deepEqual(await r.json(),{error:'upstream_unavailable'});
});
test('verify checks protected admin access and workflow without sending push',async t=>{
  const calls=mock(t,[Response.json({}),Response.json({state:'active'})]);
  const r=await worker.fetch(request({path:'/verify',body:{}}),{GITHUB_TOKEN:'test-secret'});
  assert.equal((await r.json()).status,'connected');
  assert.match(calls[0].url,/\/adminRuntime\/bridge$/);
  assert.equal(calls[1].options.method,'GET');
});
