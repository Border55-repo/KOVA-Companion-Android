const forbidden=new Set(['__proto__','constructor','prototype']);
const leads=new Set([15,30,60,120,360,1440,2880,10080]);
const record=value=>value && typeof value==='object' && !Array.isArray(value);
function text(value,max=1000){
  if(typeof value!=='string'||value.length>max)throw new Error('Ugyldig tekst i sikkerhetskopien.');
  return value;
}
function key(value){text(value,300);if(!value||forbidden.has(value))throw new Error('Ugyldig nøkkel.');return value;}
function list(value){
  if(!Array.isArray(value)||value.length>2000)throw new Error('Ugyldig liste i sikkerhetskopien.');
  return [...new Set(value.map(key))];
}
function map(value,convert){
  if(!record(value)||Object.keys(value).length>2000)throw new Error('Ugyldige lagrede valg.');
  const out=Object.create(null);
  for(const [k,v] of Object.entries(value))out[key(k)]=convert(v);
  return out;
}
export function validateBackup(value){
  if(!record(value)||value.product!=='Kova Companion'||value.version!==1||!record(value.data)){
    throw new Error('Velg en sikkerhetskopi fra Kova Companion (format 1).');
  }
  const d=value.data;
  return {
    favorites:list(d.favorites),followed:list(d.followed),favoriteOrgs:list(d.favoriteOrgs),
    notes:map(d.notes,v=>text(v,10000)),
    favoriteMeta:map(d.favoriteMeta,v=>{
      if(!record(v))throw new Error('Ugyldig vaktinformasjon.');
      const out={};
      for(const field of ['orgCode','eventId','semanticKey','anchorDate','dateIso','time','description','type'])out[field]=text(v[field]||'',2000);
      return out;
    }),
    reminders:map(d.reminders,v=>{
      const minutes=typeof v==='number'?v:v?.leadMinutes;
      if(!leads.has(minutes))throw new Error('Ugyldig påminnelsesvalg.');
      return {leadMinutes:minutes,anchorDate:text(v?.anchorDate||'',16),synced:false,needsRegistration:true};
    })
  };
}
export function makeBackup(state,now=new Date()){
  return {product:'Kova Companion',version:1,exportedAt:now.toISOString(),data:validateBackup({
    product:'Kova Companion',version:1,data:{
      favorites:[...state.favorites],followed:[...state.followed],favoriteOrgs:[...state.favoriteOrgs],
      notes:state.notes,favoriteMeta:state.favoriteMeta,reminders:state.reminders
    }
  })};
}
export function mergeBackup(current,incoming){
  const result={};
  for(const field of ['favorites','followed','favoriteOrgs'])result[field]=[...new Set([...current[field],...incoming[field]])];
  // Existing local entries win; importing must never overwrite a newer note or reminder.
  for(const field of ['notes','favoriteMeta','reminders'])result[field]={...incoming[field],...current[field]};
  return result;
}
export function storeBackup(storage,data){
  const fields=['favorites','followed','favoriteOrgs','notes','favoriteMeta','reminders'];
  const previous=fields.map(field=>storage.getItem('kova.pwa.'+field));
  try{for(const field of fields)storage.setItem('kova.pwa.'+field,JSON.stringify(data[field]));}
  catch(error){
    for(const field of fields)storage.removeItem("kova.pwa."+field);
    for(let i=0;i<fields.length;i++){
      const name='kova.pwa.'+fields[i];
      if(previous[i]===null)storage.removeItem(name);else storage.setItem(name,previous[i]);
    }
    throw error;
  }
}
