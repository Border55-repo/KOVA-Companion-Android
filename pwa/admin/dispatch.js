const ENDPOINT = 'https://kova-dispatch.juliannordli.workers.dev';

function failureMessage(response, result) {
  if (result.error === 'github_rejected') {
    const status = Number(result.upstreamStatus);
    return status === 401 ? 'GitHub avviser nøkkelen. Kontroller Cloudflare-hemmeligheten GITHUB_TOKEN.'
      : status === 403 ? 'GitHub-nøkkelen mangler tilgang til Actions, eller tilgangen er blokkert.'
      : status === 404 ? 'GitHub finner ikke workflowen, eller nøkkelen mangler repo-tilgang.'
      : `GitHub avviste oppstart (HTTP ${status || response.status}).`;
  }
  if (result.error === 'github_token_missing') return 'Hurtigstart er ikke ferdig konfigurert.';
  if ([401,403].includes(response.status)) return 'Admininnloggingen må fornyes.';
  if (result.error === 'firebase_unavailable') return 'Firebase svarer ikke som forventet.';
  if (result.error === 'upstream_unavailable') return 'Tilkoblingen til Firebase eller GitHub feilet eller tok for lang tid.';
  return 'Hurtigstart er midlertidig utilgjengelig.';
}

export async function verifyConnection(user) {
  if (!user) return {ok:false, message:'Logg inn for å kontrollere tilkoblingen.'};
  try {
    const response = await fetch(`${ENDPOINT}/verify`, {
      method:'POST', headers:{Authorization:`Bearer ${await user.getIdToken()}`, 'Content-Type':'application/json'},
      body:'{}', signal:AbortSignal.timeout(30000)
    });
    const result = await response.json();
    return response.ok && result.status === 'connected'
      ? {ok:true, message:'Firebase og GitHub svarer. Tilkoblingen er kontrollert uten å sende varsler.'}
      : {ok:false, message:failureMessage(response,result)};
  } catch { return {ok:false, message:'Kunne ikke kontrollere tilkoblingen. Prøv igjen.'}; }
}

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
    const reason = failureMessage(response,result);
    return {ok:false, message:`${reason} Forespørselen er lagret og venter på neste planlagte kjøring.`};
  } catch {
    return {ok:false, message:'Kunne ikke bekrefte hurtigstart. Forespørselen er lagret; den planlagte kjøringen er reserve.'};
  }
}
