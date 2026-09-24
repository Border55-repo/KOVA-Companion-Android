import test from 'node:test';
import assert from 'node:assert/strict';
import {requestDispatch} from '../admin/dispatch.js';
const user={getIdToken:async()=> 'firebase-test-token'};
test('admin starts an existing request with Firebase auth only',async t=>{
  t.mock.method(globalThis,'fetch',async(url,options)=>{
    assert.equal(url,'https://kova-dispatch.juliannordli.workers.dev/dispatch');
    assert.equal(options.headers.Authorization,'Bearer firebase-test-token');
    assert.deepEqual(JSON.parse(options.body),{command:'announcement',requestId:'existing-123'});
    return Response.json({status:'queued'},{status:202});
  });
  assert.equal((await requestDispatch(user,'announcement','existing-123')).ok,true);
});
test('missing worker secret preserves saved-request fallback messaging',async t=>{
  t.mock.method(globalThis,'fetch',async()=>Response.json({error:'github_token_missing'},{status:503}));
  const result=await requestDispatch(user,'announcement','existing-123');
  assert.equal(result.ok,false);
  assert.match(result.message,/ikke ferdig konfigurert/);
  assert.match(result.message,/lagret/);
});
test('network timeout does not tell admin to publish a duplicate',async t=>{
  t.mock.method(globalThis,'fetch',async()=>{throw new Error('timeout')});
  const result=await requestDispatch(user,'bridgeSync','existing-123');
  assert.equal(result.ok,false);
  assert.match(result.message,/lagret/);
});
test('signed-out admin makes no network request',async t=>{
  t.mock.method(globalThis,'fetch',()=>assert.fail('must not fetch'));
  assert.equal((await requestDispatch(null,'announcement','existing-123')).ok,false);
});
