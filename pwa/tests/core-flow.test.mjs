import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const app=await readFile('pwa/app.js','utf8');
const event={id:'shift',orgCode:'A',dateIso:'2026-10-01'};
function reminders(sync){
  const state={reminders:{},favorites:new Set(),favoriteMeta:{},events:[event],favoriteEvents:[]};
  const context=vm.createContext({state,Notification:{permission:'granted'},
    eventKey:e=>e.id,reminderEntryFor:e=>state.reminders[e.id],
    reminderMinutesFor:e=>state.reminders[e.id]?.leadMinutes||0,
    rememberFavoriteEvent:()=>{},saveSet:()=>{},saveReminders:()=>{},syncReminderBackend:sync});
  vm.runInContext(app.slice(app.indexOf('async function saveReminder('),app.indexOf('async function syncPushOrganizations(')),context);
  return {state,context};
}
test('selecting a reminder saves the shift without requiring corps alerts',async()=>{
  const r=reminders(async()=>true);
  assert.equal(await r.context.saveReminder(event,60),true);
  assert.ok(r.state.favorites.has('shift'));
  assert.equal(r.state.reminders.shift.synced,true);
});
test('failed server registration preserves a clearly unconfirmed local reminder',async()=>{
  const r=reminders(async()=>{throw Error('offline')});
  await assert.rejects(r.context.saveReminder(event,60),/offline/);
  assert.equal(r.state.reminders.shift.synced,false);
  r.context.syncReminderBackend=async()=>true;
  await r.context.syncAllReminders();
  assert.equal(r.state.reminders.shift.synced,true);
});
for(const failure of [false,Error('offline')]){
  test(`cancellation retains reminder when server ${failure===false?'is unavailable':'fails'}`,async()=>{
    const r=reminders(async()=>{if(failure instanceof Error)throw failure;return failure});
    r.state.reminders.shift={leadMinutes:60,synced:true};
    await assert.rejects(r.context.saveReminder(event,0));
    assert.equal(r.state.reminders.shift.leadMinutes,60);
  });
}
test('confirmed cancellation removes the local reminder',async()=>{
  const r=reminders(async(e,m,enabled)=>!enabled);
  r.state.reminders.shift={leadMinutes:60,synced:true};
  await r.context.saveReminder(event,0);
  assert.equal(r.state.reminders.shift,undefined);
});
test('backend registration does not depend on a general corps subscription',async()=>{
  let write;
  const context=vm.createContext({Notification:{permission:'granted'},state:{org:'A',favoriteOrgs:new Set()},
    currentPushSubscription:async()=>({toJSON:()=>({endpoint:'endpoint'})}),endpointId:async()=> 'sub',reminderDocumentId:async()=> 'rem',reminderToken:()=> 'secret',semanticKey:()=> 'semantic',
    firestoreClient:async()=>({db:{},doc:()=> 'doc',setDoc:async(d,v)=>write=v,serverTimestamp:()=> 'now'})});
  vm.runInContext(app.slice(app.indexOf('async function syncReminderBackend('),app.indexOf('async function saveReminder(')),context);
  assert.equal(await context.syncReminderBackend(event,60),true);
  assert.equal(write.organization,'A');
  assert.equal(write.subscriptionId,'sub');
});
test('received shift history opens its stored event target and announcements remain readable',()=>{
  const container={innerHTML:'',classList:{toggle(){},remove(){}},children:[],appendChild(el){this.children.push(el)}};
  let opened;
  const context=vm.createContext({$:()=>container,document:{createElement:tag=>({tag,append(){}})},formatUpdated:()=>'',orgName:s=>s,openNotificationTarget:async item=>opened=item});
  vm.runInContext(app.slice(app.indexOf('function renderNotificationHistory('),app.indexOf('function requestNotificationHistory(')),context);
  const item={eventId:'shift',description:'Old shift',kind:'reminder'};
  context.renderNotificationHistory([item,{title:'Announcement'}]);
  assert.equal(container.children[0].tag,'button');
  container.children[0].onclick();
  assert.equal(opened,item);
  assert.equal(container.children[1].tag,'div');
});
test('history storage failure is reported to the app rather than shown as empty',async()=>{
  const handlers={};let result;let done;
  const context=vm.createContext({self:{addEventListener:(name,fn)=>handlers[name]=fn}});
  vm.runInContext(await readFile('pwa/sw.js','utf8'),context);
  vm.runInContext('readHistory=async()=>{throw Error("unavailable")}',context);
  handlers.message({data:{type:'get-notification-history'},source:{postMessage:value=>result=value},waitUntil:p=>done=p});
  await done;
  assert.equal(result.error,true);
});
