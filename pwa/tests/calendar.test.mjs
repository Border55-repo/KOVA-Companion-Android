import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
import {dateKey,monthDays,moveMonth} from '../calendar.js';

test('month layout starts on Monday and includes leap days',()=>{
  const days=monthDays('2028-02');
  assert.equal(days[0],null);
  assert.equal(days[1],'2028-02-01');
  assert.equal(days.at(-1),'2028-02-29');
  assert.equal(monthDays('2027-02').filter(Boolean).length,28);
});
test('month navigation crosses the year boundary in both directions',()=>{
  assert.equal(moveMonth('2026-12',1),'2027-01');
  assert.equal(moveMonth('2027-01',-1),'2026-12');
  assert.equal(dateKey(new Date(2026,8,29,0,1)),'2026-09-29');
});
const app=await readFile('pwa/app.js','utf8');
function filterContext(){
  const state={date:'2026-12-31',view:'week',search:'',type:'',favorites:new Set(['b']),events:[
    {id:'a',dateIso:'2026-10-01',time:'10:00',type:'Øvelse',description:'Søk'},
    {id:'b',dateIso:'2026-12-31',time:'12:00',type:'Vakt',description:'Beredskap'},
    {id:'c',dateIso:'2026-12-31',time:'08:00',type:'Vakt',description:'Søk'},
  ]};
  const context=vm.createContext({state,isPastShift:e=>e.dateIso<'2026-10-01',eventKey:e=>e.id,dateInRange:date=>date==='2026-10-01'});
  vm.runInContext(app.slice(app.indexOf('function filteredEvents('),app.indexOf('function render(){')),context);
  return {state,filter:context.filteredEvents};
}
test('an explicit day overrides the rolling period and sorts shifts by time',()=>{
  const {filter}=filterContext();
  assert.equal(filter().map(e=>e.id).join(','),'c,b');
});
test('days without available shifts are empty and clearing restores the period',()=>{
  const {state,filter}=filterContext();
  state.date='2026-12-30';
  assert.equal(filter().length,0);
  state.date='';
  assert.equal(filter().map(e=>e.id).join(','),'a');
});
test('personal shifts and search remain respected by calendar selection and counts',()=>{
  const {state,filter}=filterContext();
  state.view='favorites';
  assert.equal(filter().map(e=>e.id).join(','),'b');
  assert.equal(filter('',true).map(e=>e.id).join(','),'b');
  state.search='Søk';
  assert.equal(filter().length,0);
  state.view='all';
  assert.equal(filter('',true).map(e=>e.id).join(','),'a,c');
});
