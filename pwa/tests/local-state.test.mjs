import test from 'node:test';
import assert from 'node:assert/strict';
import {readStoredStrings,readStoredRecord} from '../local-state.js';

const storage = values => ({getItem:key => values[key] ?? null});

test('damaged local preferences cannot prevent startup', () => {
  const saved=storage({favorites:'{broken',notes:'["wrong shape"]',followed:'42'});
  assert.deepEqual(readStoredStrings(saved,'favorites'),[]);
  assert.deepEqual(readStoredRecord(saved,'notes'),{});
  assert.deepEqual(readStoredStrings(saved,'followed',['UllensakerRKH']),['UllensakerRKH']);
});

test('valid preferences survive and invalid set members are ignored', () => {
  const saved=storage({favorites:'["A|shift",null,42,"B|shift"]',notes:'{"A|shift":"Ta med radio"}'});
  assert.deepEqual(readStoredStrings(saved,'favorites'),['A|shift','B|shift']);
  assert.deepEqual(readStoredRecord(saved,'notes'),{'A|shift':'Ta med radio'});
});

test('read failures use defaults without deleting stored data', () => {
  const blocked={getItem(){throw new Error('Storage blocked')}};
  assert.deepEqual(readStoredStrings(blocked,'followed',['UllensakerRKH']),['UllensakerRKH']);
  assert.deepEqual(readStoredRecord(blocked,'reminders'),{});
});
