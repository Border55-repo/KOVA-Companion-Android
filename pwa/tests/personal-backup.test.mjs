import test from 'node:test';
import assert from 'node:assert/strict';
import {makeBackup,validateBackup,mergeBackup,storeBackup} from '../personal-backup.js';
import {detectPlatform} from '../guidance.js';
function sample(){return {favorites:new Set(['A|one']),followed:new Set(['A']),favoriteOrgs:new Set(['A']),
  notes:{'A|one':'Privat notat'},favoriteMeta:{'A|one':{orgCode:'A',eventId:'one',description:'Vakt',dateIso:'2026-11-01'}},
  reminders:{'A|one':{leadMinutes:60,synced:true}},reminderToken:'secret',endpoint:'secret',pushRegisteredAt:'secret'};}
test('backup contains user data but never device credentials or server confirmation',()=>{
  const file=makeBackup(sample());const json=JSON.stringify(file);
  assert.ok(json.includes('Privat notat'));
  assert.ok(!json.includes('secret'));
  const data=validateBackup(JSON.parse(json));
  assert.equal(data.reminders['A|one'].synced,false);
  assert.equal(data.reminders['A|one'].needsRegistration,true);
});
test('import retains newer existing notes and registered reminders',()=>{
  const backup=makeBackup(sample()).data;
  const current={...backup,notes:{'A|one':'Nyere notat'},reminders:{'A|one':{leadMinutes:30,synced:true}}};
  backup.notes['B|two']='Importert';backup.favorites.push('B|two');
  const result=mergeBackup(current,backup);
  assert.equal(result.notes['A|one'],'Nyere notat');assert.equal(result.notes['B|two'],'Importert');
  assert.equal(result.reminders['A|one'].synced,true);
  assert.equal(result.reminders['A|one'].leadMinutes,30);
});
test('malformed versions, prototype keys, excessive text and unsupported reminders are rejected',()=>{
  assert.throws(()=>validateBackup({product:'Other',version:1}));
  const file=makeBackup(sample());file.version=2;assert.throws(()=>validateBackup(file));file.version=1;
  file.data.notes=JSON.parse('{"__proto__":{"polluted":true}}');assert.throws(()=>validateBackup(file));
  file.data.notes={'A|one':'x'.repeat(10001)};assert.throws(()=>validateBackup(file));file.data.notes={};
  file.data.reminders={'A|one':{leadMinutes:-1}};assert.throws(()=>validateBackup(file));
  assert.equal({}.polluted,undefined);
});
test('quota failure rolls storage back instead of leaving half an import',()=>{
  const items=new Map([['kova.pwa.favorites','["existing"]'],['kova.pwa.notes','{"x":"keep"}']]);
  const before=new Map(items);let writes=0;
  const storage={getItem:k=>items.get(k)??null,removeItem:k=>items.delete(k),setItem(k,v){
    if(++writes===3)throw new Error('QuotaExceeded');items.set(k,v);
  }};
  assert.throws(()=>storeBackup(storage,makeBackup(sample()).data));
  assert.deepEqual(items,before);
});
test('platform guidance recognizes Android, iPhone and iPad desktop mode',()=>{
  assert.equal(detectPlatform('Mozilla Android'),'android');
  assert.equal(detectPlatform('Mozilla iPhone'),'ios');
  assert.equal(detectPlatform('Mozilla Macintosh',5),'ios');
  assert.equal(detectPlatform('Mozilla Macintosh',0),'desktop');
});
