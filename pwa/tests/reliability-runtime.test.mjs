import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const app=await readFile('pwa/app.js','utf8');
const loading=app.slice(app.indexOf('function snapshotFile('),app.indexOf('async function loadCurrent('));
test('new worker installs fresh shell assets instead of recycling the HTTP cache',async()=>{
  const source=await readFile('pwa/sw.js','utf8');
  const handlers={};let requested;
  const ctx=vm.createContext({URL,Request,console,self:{location:{href:'https://example.test/app/sw.js'},skipWaiting(){},addEventListener:(name,fn)=>handlers[name]=fn},caches:{open:async()=>({addAll:async requests=>requested=requests})}});
  vm.runInContext(source,ctx);
  let done;handlers.install({waitUntil:p=>done=p});await done;
  assert.ok(requested.length>0);
  assert.ok(requested.every(request=>request.cache==='reload'));
  assert.ok(requested.some(request=>request.url==='https://example.test/app/app.js'));
});
test('snapshot URLs follow Bridge filenames for spaces and Norwegian letters',()=>{
  const ctx=vm.createContext({state:{orgIndex:new Map([['AÅRKH',{file:'A_RKH.json'}]])}});
  vm.runInContext(loading,ctx);
  assert.equal(ctx.snapshotFile('AÅRKH'),'A_RKH.json');
  assert.equal(ctx.snapshotFile('Skedsmo RKH'),'Skedsmo_RKH.json');
  assert.equal(ctx.snapshotFile('VÅRKH'),'V_RKH.json');
});
test('parallel requests for one corps share one fetch and failures can retry',async()=>{
  let calls=0,finish;
  const ctx=vm.createContext({DATA_BASE:'https://example.test',state:{orgIndex:new Map()},
    fetchJson:()=>{calls++;return new Promise(resolve=>finish=resolve)},
    orgName:()=> 'Korps',reconcileFavorites(){}});
  vm.runInContext(loading,ctx);
  const a=ctx.loadOneOrg('Skedsmo RKH'),b=ctx.loadOneOrg('Skedsmo RKH');
  assert.equal(calls,1);
  finish({events:[]});await Promise.all([a,b]);
  ctx.fetchJson=async()=>{calls++;throw new Error('offline')};
  await assert.rejects(ctx.loadOneOrg('Skedsmo RKH'));
  ctx.fetchJson=async()=>{calls++;return {events:[]}};
  await ctx.loadOneOrg('Skedsmo RKH');
  assert.equal(calls,3);
});
test('focus and visibility events share one foreground refresh',async()=>{
  const source=app.slice(app.indexOf('let lastForegroundRefresh='),app.indexOf('window.addEventListener("online"'));
  let checks=0,finish,loads=0;
  const ctx=vm.createContext({Date,console,checkRemoteCacheEpoch:()=>{checks++;return new Promise(resolve=>finish=resolve)},loadEvents:async()=>loads++,repairPushOnResume:async()=>{}});
  vm.runInContext(source,ctx);
  const a=ctx.refreshOnForeground(),b=ctx.refreshOnForeground();
  assert.equal(checks,1);finish(false);await Promise.all([a,b]);assert.equal(loads,1);
});
