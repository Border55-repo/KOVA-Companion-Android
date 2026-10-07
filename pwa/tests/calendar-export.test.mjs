import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFileSync} from 'node:fs';
const source=readFileSync('pwa/app.js','utf8');
function runtime(navigator={}){
  const hint={textContent:''}, downloads=[];
  const context={Date,Intl,TextEncoder,File,navigator,console,
    eventTime:e=>e.time,eventKey:e=>e.id,reminderMinutesFor:()=>15,orgName:()=> 'Korps',
    $:()=>hint,URL:{createObjectURL:()=> 'blob:test',revokeObjectURL(){}},setTimeout(){},
    document:{body:{appendChild(){}},createElement(){return {click(){downloads.push(this.download)},remove(){}}}}
  };
  vm.createContext(context);
  vm.runInContext(source.slice(source.indexOf('function calendarDate('),source.indexOf('async function shareEvent(')),context);
  return {context,hint,downloads};
}
const event={id:'a',dateIso:'2026-10-07',time:'18:00',description:'Øvelse, førstehjelp; prøve\r\nNy linje',type:'Øvelse'};
test('calendar exports Oslo time in UTC, required stamp, escaped and folded Unicode',async()=>{
  const {context}=runtime();
  const text=await context.buildCalendarFile({...event,description:event.description+'ø'.repeat(120)}).text();
  assert.match(text,/DTSTART:20261007T160000Z/);
  assert.match(text,/DTSTAMP:\d{8}T\d{6}Z/);
  assert.match(text,/TRIGGER:-PT15M/);
  assert.match(text,/førstehjelp\\; prøve\\nNy linje/);
  assert.ok(text.split('\r\n').every(line=>Buffer.byteLength(line)<=75));
  assert.equal(context.osloUtcStamp('2026-12-01','18:00'),'20261201T170000Z');
});
test('all-day events end on following date across year boundary',async()=>{
  const text=await runtime().context.buildCalendarFile({...event,dateIso:'2026-12-31',time:''}).text();
  assert.match(text,/DTSTART;VALUE=DATE:20261231/);
  assert.match(text,/DTEND;VALUE=DATE:20270101/);
});
test('failed sharing falls back to download; user cancellation does not',async()=>{
  const failed=runtime({canShare:()=>true,share:async()=>{throw new Error('unsupported')}});
  await failed.context.addToCalendar(event);
  assert.deepEqual(failed.downloads,['kova-aktivitet.ics']);
  const cancelled=runtime({canShare:()=>true,share:async()=>{throw {name:'AbortError'}}});
  await cancelled.context.addToCalendar(event);
  assert.equal(cancelled.downloads.length,0);
  assert.match(cancelled.hint.textContent,/avbrutt/);
});

test('morning time accepts a single digit hour',()=>{
  assert.equal(runtime().context.osloUtcStamp('2026-10-07','9:00'),'20261007T070000Z');
});
test('Mine vakter hides yesterday after midnight in Oslo, keeping today and unknown dates',()=>{
  const context={Intl,Date};vm.createContext(context);
  vm.runInContext(source.slice(source.indexOf('function isPastShift('),source.indexOf('function sortUpcoming(')),context);
  const now=new Date('2026-10-07T22:01:00Z');
  assert.equal(context.isPastShift({dateIso:'2026-10-07'},now),true);
  assert.equal(context.isPastShift({dateIso:'2026-10-08'},now),false);
  assert.equal(context.isPastShift({dateIso:''},now),false);
});
