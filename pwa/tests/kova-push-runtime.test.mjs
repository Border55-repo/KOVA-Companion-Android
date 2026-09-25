import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFile } from 'node:fs/promises';

const source=await readFile('pwa/sw.js','utf8');
function worker(){
  const handlers={};
  const shown=[];
  const writes=[];
  const opened=[];
  const context=vm.createContext({
    URL,URLSearchParams,Request,Response,console,
    self:{
      location:{href:'https://example.test/KOVA-Companion-Android/sw.js',origin:'https://example.test'},
      addEventListener:(name,handler)=>handlers[name]=handler,
      registration:{showNotification:async(title,options)=>shown.push({title,options})},
      clients:{matchAll:async()=>[],openWindow:async url=>opened.push(url)}
    },
    caches:{open:async()=>({put:async(key,response)=>writes.push({key,body:await response.text()})}),match:async()=>null},
    fetch:async()=>new Response('admin page'),
  });
  vm.runInContext(source,context);
  return {context,handlers,shown,writes,opened};
}
async function push(w,payload){
  let done;
  w.handlers.push({data:{json:()=>payload},waitUntil:p=>done=p});
  await done;
}

test('iPhone push is displayed even when history storage fails',async()=>{
  const w=worker();
  vm.runInContext('storeNotification=async()=>{throw new Error("IndexedDB unavailable")}',w.context);
  await push(w,{title:'Endringer',body:'Test',changeId:'same-id'});
  assert.equal(w.shown.length,1);
  assert.equal(w.shown[0].options.tag,'same-id');
});

test('repeat push remains visible with the same replacement tag',async()=>{
  const w=worker();
  vm.runInContext('storeNotification=async()=>true',w.context);
  await push(w,{changeId:'same-id'});
  await push(w,{changeId:'same-id'});
  assert.equal(w.shown.length,2);
  assert.equal(w.shown[0].options.tag,w.shown[1].options.tag);
  assert.equal(w.shown[1].options.renotify,false);
});

test('admin navigation cannot overwrite the offline KOVA shell',async()=>{
  const w=worker();
  const request={method:'GET',mode:'navigate',url:'https://example.test/KOVA-Companion-Android/admin/'};
  let response;
  w.handlers.fetch({request,respondWith:p=>response=p});
  await response;
  assert.equal(w.writes[0].key,request);
  assert.notEqual(w.writes[0].key,'./index.html');
});

test('announcement click opens changelog without focusing unrelated admin tab',async()=>{
  const w=worker();
  w.context.self.clients.matchAll=async()=>[{url:'https://example.test/KOVA-Companion-Android/admin/',focus:()=>assert.fail('Wrong tab')}];
  let done;
  w.handlers.notificationclick({notification:{close(){},data:{url:'https://example.test/KOVA-Companion-Android/#changelogCard'}},waitUntil:p=>done=p});
  await done;
  assert.deepEqual(w.opened,['https://example.test/KOVA-Companion-Android/#changelogCard']);
});

test('announcement click navigates an existing app to the changelog',async()=>{
  const w=worker();
  let target;
  let focused=false;
  const client={url:'https://example.test/KOVA-Companion-Android/',navigate:async url=>{target=url;return client},focus:async()=>focused=true};
  w.context.self.clients.matchAll=async()=>[client];
  let done;
  w.handlers.notificationclick({notification:{close(){},data:{url:'https://example.test/KOVA-Companion-Android/#changelogCard'}},waitUntil:p=>done=p});
  await done;
  assert.equal(target,'https://example.test/KOVA-Companion-Android/#changelogCard');
  assert.equal(focused,true);
});

test('admin status distinguishes queue, transport acceptance, and partial failure',async()=>{
  const app=await readFile('pwa/admin/app.js','utf8');
  const fn=app.slice(app.indexOf('function announcementStatusText('),app.indexOf('function loadDashboard('));
  const context=vm.createContext({fmt:()=> '12:00'});
  vm.runInContext(fn,context);
  assert.match(context.announcementStatusText({status:'requested'}),/i kø/);
  const status=context.announcementStatusText({status:'failed',delivery:{android:{targets:43,accepted:43},pwa:{targets:1,accepted:0,failed:1}}});
  assert.match(status,/PWA: 0\/1/);
  assert.match(status,/1 feil/);
  assert.match(status,/ikke bekreftelse på visning/);
});

test('user navigation falls back to its own cached shell while offline',async()=>{
  const w=worker();
  w.context.fetch=async()=>{throw new Error('offline')};
  w.context.caches.match=async key=>key==='./index.html'?new Response('KOVA shell'):null;
  let response;
  w.handlers.fetch({request:{method:'GET',mode:'navigate',url:'https://example.test/KOVA-Companion-Android/'},respondWith:p=>response=p});
  assert.equal(await (await response).text(),'KOVA shell');
});

for(const favorites of [['UllensakerRKH'],[]]){
  test(`push registration uses only favorite corps (${favorites.length} selected)`,async()=>{
    const app=await readFile('pwa/app.js','utf8');
    const fn=app.slice(app.indexOf('async function savePushSubscription('),app.indexOf('async function reminderDocumentId('));
    let saved;
    const context=vm.createContext({
      state:{org:'EHRKH',followed:new Set(['EHRKH','Skedsmo RKH']),favoriteOrgs:new Set(favorites),notificationKinds:new Set(['added']),disabledNotificationTypes:new Set(),quietEnabled:false,quietStartHour:22,quietEndHour:7},
      endpointId:async()=> 'test-id',reminderToken:()=> 'token',
      firestoreClient:async()=>({db:{},doc:()=>({}),setDoc:async(_,data)=>saved=data,serverTimestamp:()=> 'now'}),
      DEMO:false,storage:{setItem(){},removeItem(){}},syncAllReminders:async()=>{},console,
    });
    vm.runInContext(fn,context);
    await context.savePushSubscription({toJSON:()=>({endpoint:'https://example.test/push',keys:{p256dh:'key',auth:'auth'}})});
    assert.deepEqual(Array.from(saved.organizations),favorites);
    assert.ok(saved.notificationKinds.includes('announcement'));
  });
}

test('first permission request starts directly in the iPhone tap handler',async()=>{
  const app=await readFile('pwa/app.js','utf8');
  const fn=app.slice(app.indexOf('async function toggleNotifications('),app.indexOf('function renderOrgOptions('));
  let prompted=false;
  const context=vm.createContext({
    DEMO:false,Notification:{permission:'default'},
    enableNotifications:async()=>{prompted=true},
    currentPushSubscription:async()=>assert.fail('Must not await subscription lookup before requesting permission'),
  });
  vm.runInContext(fn,context);
  const done=context.toggleNotifications();
  assert.equal(prompted,true);
  await done;
});
