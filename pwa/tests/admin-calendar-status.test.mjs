import test from 'node:test';
import assert from 'node:assert/strict';
import {latestCalendarCheck} from '../admin/calendar-status.js';

const older='2026-11-22T21:10:00Z';
const newer='2026-11-22T21:15:00Z';

test('a stale runtime failure cannot override a newer published sync',()=>{
  const row={status:'ok',lastCheckedAt:newer,eventCount:152};
  assert.deepEqual(latestCalendarCheck(row,{status:'error',checkedAt:older}),row);
});

test('a newer failure is shown while the published activity count is retained',()=>{
  const row={status:'ok',lastCheckedAt:older,eventCount:152};
  assert.deepEqual(latestCalendarCheck(row,{status:'error',checkedAt:newer,error:'Kilde utilgjengelig'}),{
    status:'error',lastCheckedAt:newer,eventCount:152,error:'Kilde utilgjengelig'
  });
});
