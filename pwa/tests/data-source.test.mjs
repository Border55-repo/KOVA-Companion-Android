import test from 'node:test';
import assert from 'node:assert/strict';
import {createSnapshotSource,freshness} from '../data-source.js';

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
