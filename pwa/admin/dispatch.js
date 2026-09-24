const ENDPOINT = 'https://kova-dispatch.juliannordli.workers.dev';

export async function requestDispatch(user, command, requestId) {
  if (!user) return {ok:false, message:'Logg inn på nytt for å starte utsending.'};
  try {
    const token = await user.getIdToken();
    const response = await fetch(`${ENDPOINT}/dispatch`, {
      method:'POST', headers:{Authorization:`Bearer ${token}`, 'Content-Type':'application/json'},
      body:JSON.stringify({command, requestId}), signal:AbortSignal.timeout(30000)
    });
    const result = await response.json();
    if (response.ok && ['queued','already_processing'].includes(result.status)) {
      return {ok:true, message:'Utsending er bestilt. GitHub behandler jobben; leveringsstatus vises under.'};
    }
    const reason = result.error === 'github_token_missing' ? 'Hurtigstart er ikke ferdig konfigurert.'
      : [401,403].includes(response.status) ? 'Admininnloggingen må fornyes.'
      : 'Hurtigstart er midlertidig utilgjengelig.';
    return {ok:false, message:`${reason} Forespørselen er lagret og venter på neste planlagte kjøring.`};
  } catch {
    return {ok:false, message:'Kunne ikke bekrefte hurtigstart. Forespørselen er lagret; den planlagte kjøringen er reserve.'};
  }
}
