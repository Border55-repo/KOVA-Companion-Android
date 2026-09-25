// Synthetic data only. Demo state stays in memory and never uses live subscriptions.
export function createDemoSource(now=new Date()){
  const day=n=>{const d=new Date(now);d.setDate(d.getDate()+n);return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`};
  const organizations=[{code:'UllensakerRKH',name:'Eksempelkorps (demo)',category:'hjelpekorps'}];
  const events=['Sanitetsvakt på idrettsarrangement','Øvelse: søk og førstehjelp','Beredskapsvakt'].map((description,i)=>({id:`demo-${i}`,dateIso:day(i+1),dateLabel:day(i+1),time:'18:00',type:i===1?'Øvelse':'Sanitetsvakt',description,sourceUrl:'',location:'Eksempelsted'}));
  return {async read(url){
    if(url.includes('organizations.json'))return {organizations};
    if(url.includes('index.json'))return {apiVersion:'2.0',organizations:organizations.map(o=>({...o,file:o.code+'.json',status:'ok',eventCount:events.length,checkedAt:now.toISOString(),updatedAt:now.toISOString()}))};
    return {organization:organizations[0],status:'ok',updatedAt:now.toISOString(),events};
  }};
}
export function memoryStorage(){const values=new Map();return {getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,String(value)),removeItem:key=>values.delete(key)}};
