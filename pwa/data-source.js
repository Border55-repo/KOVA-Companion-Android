// The app consumes normalized snapshots; future authenticated API adapters belong
// behind this boundary, never as credentials embedded in a public client.
export function createSnapshotSource(fetcher=fetch){
  return {async read(url){
    const response=await fetcher(url+(url.includes('?')?'&':'?')+`ts=${Date.now()}`,{cache:'no-store',signal:AbortSignal.timeout(15000)});
    if(!response.ok)throw new Error('Kunne ikke hente oppdaterte KOVA-data');
    const data=await response.json();
    if(!data||typeof data!=='object'||Array.isArray(data))throw new Error('Ugyldig dataformat');
    return data;
  }};
}
export function freshness(checkedAt,online=true,now=Date.now()){
  const stamp=Date.parse(checkedAt||'');
  if(!online)return 'Frakoblet – viser lagrede data';
  if(!Number.isFinite(stamp))return 'Sist kontrollert: ukjent';
  const age=Math.max(0,(now-stamp)/60000);
  return age>35?'Data kan være gamle – siste kontroll er over 35 minutter siden':'Data kontrollert nylig';
}
