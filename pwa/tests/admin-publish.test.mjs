import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const app=await readFile('pwa/admin/app.js','utf8');
test('test publication writes only its exact targeted command, never public changelog',async()=>{
  const writes=[],dispatches=[];const elements=new Map();
  const f={db:{},auth:{currentUser:{}},serverTimestamp:()=> 'now',doc:(_,collection,id)=>({collection,id}),
    runTransaction:async(_,callback)=>callback({get:async()=>({exists:()=>false}),set:(ref,value)=>writes.push({ref,value})})};
  const ctx=vm.createContext({initFirebase:async()=>f,$:id=>{if(!elements.has(id))elements.set(id,{});return elements.get(id)},
    requestDispatch:async(...args)=>{dispatches.push(args);return {message:'accepted'}},loadDashboard:async()=>{},Date,console});
  vm.runInContext(app.slice(app.indexOf('async function publishAnnouncement('),app.indexOf('$("orgSearch").addEventListener')),ctx);
  await ctx.publishAnnouncement({title:'Test',body:'Private preview',sendPush:true,audience:'test',organization:'',subscriptionId:'a'.repeat(64)});
  assert.equal(writes.length,1);assert.equal(writes[0].ref.collection,'adminCommands');
  assert.equal(writes[0].value.audience,'test');assert.equal(writes[0].value.subscriptionId,'a'.repeat(64));
  assert.equal(dispatches.length,1);
});
test('invalid device code cannot open test confirmation',()=>{
  let opened=false;const elements={changelogTitle:{value:'Test'},changelogBody:{value:'Body'},testDeviceId:{value:''},changelogMessage:{},previewDialog:{showModal:()=>opened=true}};
  const ctx=vm.createContext({$:id=>elements[id]});
  vm.runInContext(app.slice(app.indexOf('function previewAnnouncement('),app.indexOf('$("publishChangelogBtn").onclick')),ctx);
  ctx.previewAnnouncement(true);assert.equal(opened,false);assert.match(elements.changelogMessage.textContent,/enhetskoden/);
});
