import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {createDemoSource,memoryStorage} from '../demo-data.js';
import {createSnapshotSource,freshness} from '../data-source.js';

test('demo uses upcoming synthetic events and isolated transient storage',async()=>{
  const demo=createDemoSource(new Date('2026-09-25T12:00:00Z'));
  const snapshot=await demo.read('snapshot.json');
  assert.equal(snapshot.events.length,3);
  assert.equal(snapshot.events[0].dateIso,'2026-09-26');
  assert.ok(snapshot.events.every(e=>e.id.startsWith('demo-')&&!e.sourceUrl));
  const first=memoryStorage(),second=memoryStorage();first.setItem('favorite','x');
  assert.equal(second.getItem('favorite'),null);
});
test('snapshot source propagates failures and rejects malformed payloads',async()=>{
  await assert.rejects(createSnapshotSource(async()=>({ok:false})).read('https://example.test'));
  await assert.rejects(createSnapshotSource(async()=>({ok:true,json:async()=>[]})).read('https://example.test'));
});
test('freshness distinguishes offline, unknown, stale and recent checks',()=>{
  const now=Date.parse('2026-09-25T12:00:00Z');
  assert.match(freshness(null,true,now),/ukjent/);
  assert.match(freshness('2026-09-25T11:00:00Z',true,now),/gamle/);
  assert.match(freshness('2026-09-25T11:50:00Z',true,now),/nylig/);
  assert.match(freshness('2026-09-25T11:50:00Z',false,now),/Frakoblet/);
});
test('demo push and reminder operations cannot touch live APIs',async()=>{
  const app=await readFile('pwa/app.js','utf8');
  const ctx=vm.createContext({DEMO:true});
  for(const [start,end] of [
    ['async function savePushSubscription(', 'async function reminderDocumentId('],
    ['async function syncReminderBackend(', 'async function saveReminder('],
    ['async function currentPushSubscription(', 'async function savePushSubscription(']
  ]) vm.runInContext(app.slice(app.indexOf(start),app.indexOf(end)),ctx);
  await ctx.savePushSubscription({});
  assert.equal(await ctx.syncReminderBackend({},30),true);
  assert.equal(await ctx.currentPushSubscription(),null);
});
