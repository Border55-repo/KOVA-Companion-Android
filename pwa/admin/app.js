import {requestDispatch,verifyConnection} from './dispatch.js';
const DATA_BASE="https://raw.githubusercontent.com/Border55-repo/KOVA-Companion-Android/main/bridge/data";
const ADMIN_EMAIL="superuser@kova-companion.local";
const $=id=>document.getElementById(id);
let firebase=null;
let currentOrgRows=[];
let dashboardTimer=null;
let dashboardLoad=null;
let approvedDraft=null;

async function initFirebase(){
  if(firebase)return firebase;
  const [appModule,authModule,firestoreModule,config]=await Promise.all([
    import("https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js"),
    import("https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js"),
    import("https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js"),
    fetch("../firebase-web-config.json",{cache:"no-store"}).then(r=>{
      if(!r.ok)throw new Error("Kunne ikke hente Firebase-konfigurasjon");
      return r.json();
    })
  ]);
  const app=appModule.getApps().length?appModule.getApps()[0]:appModule.initializeApp(config);
  firebase={
    app,
    auth:authModule.getAuth(app),
    signInWithEmailAndPassword:authModule.signInWithEmailAndPassword,
    signOut:authModule.signOut,
    onAuthStateChanged:authModule.onAuthStateChanged,
    updatePassword:authModule.updatePassword,
    db:firestoreModule.getFirestore(app),
    doc:firestoreModule.doc,
    getDoc:firestoreModule.getDoc,
    setDoc:firestoreModule.setDoc,
    runTransaction:firestoreModule.runTransaction,
    updateDoc:firestoreModule.updateDoc,
    collection:firestoreModule.collection,
    getDocs:firestoreModule.getDocs,
    serverTimestamp:firestoreModule.serverTimestamp
  };
  return firebase;
}

function show(id){
  if(id!=="dashboardView"&&dashboardTimer){clearTimeout(dashboardTimer);dashboardTimer=null}
  for(const name of ["loginView","passwordView","dashboardView"])$(name).classList.toggle("hidden",name!==id);
}
function err(id,message=""){
  $(id).textContent=message;
  $(id).classList.toggle("hidden",!message);
}
async function fetchJson(path){
  const sep=path.includes("?")?"&":"?";
  const response=await fetch(path+`${sep}ts=${Date.now()}`,{cache:"no-store"});
  if(!response.ok)throw new Error(`HTTP ${response.status}`);
  return response.json();
}
function dateValue(value){
  if(!value)return null;
  if(typeof value?.toDate==="function")return value.toDate();
  const date=new Date(value);
  return Number.isNaN(date.getTime())?null:date;
}
function fmt(value){
  const date=dateValue(value);
  if(!date)return "–";
  return new Intl.DateTimeFormat("nb-NO",{dateStyle:"short",timeStyle:"short"}).format(date);
}
function ageMinutes(value){
  const date=dateValue(value);
  return date?Math.max(0,(Date.now()-date.getTime())/60000):Infinity;
}
function healthState(runtime){
  if(!runtime?.lastRunAt)return {level:"unknown",label:"Ukjent",hint:"Venter på første live Bridge-status."};
  const age=ageMinutes(runtime.lastRunAt);
  const runFailures=Number(runtime.failures||0);
  const pending=Number(runtime.pendingPushes||0);
  const runtimeStatus=String(runtime.status||"").toLowerCase();
  if(runtimeStatus==="error"||runFailures>0){
    return {level:"error",label:"Feil",hint:`Siste Bridge-kjøring: ${fmt(runtime.lastRunAt)} • ${runFailures} feil`};
  }
  if(runtimeStatus==="degraded"||pending>0){
    return {level:"warning",label:"Advarsel",hint:`Siste Bridge-kjøring: ${fmt(runtime.lastRunAt)}${pending>0?` • ${pending} push venter`:""}`};
  }
  if(age>90)return {level:"warning",label:"Forsinket",hint:`Siste vellykkede Bridge-kjøring: ${fmt(runtime.lastRunAt)}`};
  return {level:"ok",label:"OK",hint:`Siste vellykkede Bridge-kjøring: ${fmt(runtime.lastRunAt)}`};
}
function renderSystemHealth(runtime){
  const state=healthState(runtime);
  const card=$("systemHealth");
  card.classList.remove("health-ok","health-warning","health-error","health-unknown");
  card.classList.add("health-"+state.level);
  $("systemHealthLabel").textContent=state.label;
  $("systemHealthHint").textContent=state.hint;
}

function bridgeCommandView(command){
  const status=command?.status||"idle";
  if(status==="requested"){
    return {
      busy:true,
      button:"Synk bestilt",
      className:"muted status-warning",
      text:`Bestilt ${fmt(command.requestedAt)} • venter på neste Bridge-runde.`
    };
  }
  if(status==="running"){
    return {
      busy:true,
      button:"Bridge synker…",
      className:"muted status-warning",
      text:`Kjører nå • startet ${fmt(command.startedAt)}.`
    };
  }
  if(status==="completed"){
    return {
      busy:false,
      button:"Be om Bridge-synk",
      className:"muted status-ok",
      text:`Ferdig ${fmt(command.completedAt)} • ${command.polled??"–"} korps • ${command.failures??0} feil.`
    };
  }
  if(status==="failed"){
    return {
      busy:false,
      button:"Prøv Bridge-synk igjen",
      className:"muted status-error",
      text:`Synk feilet ${fmt(command.completedAt)} • ${command.polled??"–"} korps • ${command.failures??"–"} feil.`
    };
  }
  return {
    busy:false,
    button:"Be om Bridge-synk",
    className:"muted",
    text:"Ingen aktiv synkforespørsel."
  };
}
function renderBridgeSync(command){
  const view=bridgeCommandView(command);
  const status=$("bridgeSyncStatus");
  const button=$("bridgeSyncBtn");
  status.className=view.className;
  status.textContent=view.text;
  button.disabled=view.busy;
  button.textContent=view.button;
}
function scheduleDashboardRefresh(command){
  if(dashboardTimer)clearTimeout(dashboardTimer);
  if(document.hidden||$("dashboardView").classList.contains("hidden"))return;
  const active=["requested","running"].includes(command?.status);
  dashboardTimer=setTimeout(()=>{
    loadDashboard().catch(error=>{
      $("lastRefresh").textContent="Automatisk oppdatering feilet: "+(error.message||String(error));
      scheduleDashboardRefresh(command);
    });
  },active?10000:60000);
}
async function ensureProfile(user){
  const f=await initFirebase();
  const ref=f.doc(f.db,"adminUsers",user.uid);
  let snap=await f.getDoc(ref);
  if(!snap.exists()){
    await f.setDoc(ref,{
      username:"superuser",
      email:ADMIN_EMAIL,
      role:"superuser",
      mustChangePassword:true,
      createdAt:f.serverTimestamp(),
      updatedAt:f.serverTimestamp()
    });
    snap=await f.getDoc(ref);
  }
  return {ref,data:snap.data()};
}
async function handleUser(user){
  if(!user){show("loginView");return}
  if(user.email!==ADMIN_EMAIL){
    await (await initFirebase()).signOut((await initFirebase()).auth);
    err("loginError","Denne kontoen har ikke admin-tilgang.");
    show("loginView");
    return;
  }
  try{
    const profile=await ensureProfile(user);
    if(profile.data.mustChangePassword){show("passwordView");return}
    show("dashboardView");
    await loadDashboard();
  }catch(error){
    show("loginView");
    err("loginError","Adminprofil kunne ikke åpnes: "+(error.message||String(error)));
  }
}

$("loginForm").addEventListener("submit",async event=>{
  event.preventDefault();err("loginError");
  const username=$("username").value.trim().toLowerCase();
  if(username!=="superuser"){err("loginError","Ukjent brukernavn.");return}
  try{
    const f=await initFirebase();
    await f.signInWithEmailAndPassword(f.auth,ADMIN_EMAIL,$("password").value);
  }catch(error){
    const code=String(error?.code||"");
    const message=
      code.includes("auth/invalid-credential") ? "Feil brukernavn/passord, eller superuser-kontoen finnes ikke i Firebase." :
      code.includes("auth/user-not-found") ? "Superuser-kontoen finnes ikke i Firebase Authentication." :
      code.includes("auth/wrong-password") ? "Passordet er feil." :
      code.includes("auth/operation-not-allowed") ? "Email/Password-innlogging er ikke aktivert i Firebase Authentication." :
      code.includes("auth/network-request-failed") ? "Kunne ikke kontakte Firebase. Sjekk nettforbindelsen." :
      "Innlogging feilet: "+(error?.message||code||"ukjent feil");
    err("loginError",message);
  }
});

$("passwordForm").addEventListener("submit",async event=>{
  event.preventDefault();err("passwordError");
  const a=$("newPassword").value,b=$("confirmPassword").value;
  if(a.length<12){err("passwordError","Nytt passord må ha minst 12 tegn.");return}
  if(a!==b){err("passwordError","Passordene er ikke like.");return}
  try{
    const f=await initFirebase();
    const user=f.auth.currentUser;
    if(!user)throw new Error("Ikke innlogget");
    await f.updatePassword(user,a);
    await f.updateDoc(f.doc(f.db,"adminUsers",user.uid),{
      mustChangePassword:false,
      passwordChangedAt:f.serverTimestamp(),
      updatedAt:f.serverTimestamp()
    });
    show("dashboardView");
    await loadDashboard();
  }catch(error){
    err("passwordError","Passordbytte feilet: "+(error.message||String(error)));
  }
});

async function loadOrganizations(){
  const index=await fetchJson(`${DATA_BASE}/index.json`);
  const rows=(index.organizations||[]).filter(o=>o.category==="hjelpekorps");
  return {
    count:rows.length,
    rows:rows.map(org=>({
      name:org.name,
      code:org.code,
      status:org.status||"unknown",
      eventCount:Number(org.eventCount||0),
      updatedAt:org.updatedAt||"",
      lastCheckedAt:"",
      error:null
    }))
  };
}
function renderOrgRows(){
  const q=$("orgSearch").value.trim().toLowerCase();
  $("orgRows").innerHTML=currentOrgRows.filter(row=>
    !q||row.name.toLowerCase().includes(q)||row.code.toLowerCase().includes(q)
  ).map(row=>{
    const age=ageMinutes(row.lastCheckedAt);
    const freshness=!Number.isFinite(age)?"Venter":age>35?"Gammel":age>25?"Snart gammel":"Fersk";
    const statusClass=row.status==="error"?"status-error":age>35?"status-warning":"status-ok";
    const statusText=row.status==="error"?"Feil":freshness;
    return `<tr>
      <td><strong>${escapeHtml(row.name)}</strong><br><span class="muted">${escapeHtml(row.code)}</span></td>
      <td class="${statusClass}">${escapeHtml(statusText)}</td>
      <td>${row.eventCount}</td>
      <td>${escapeHtml(fmt(row.lastCheckedAt))}</td>
      <td>${escapeHtml(fmt(row.updatedAt))}</td>
    </tr>`;
  }).join("");
}
function escapeHtml(v=""){return String(v).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]))}

function announcementStatusText(command){
  if(!command)return "Ingen manuell utsending registrert.";
  const title=command.title||"Endringslogg";
  if(command.status==="requested")return `${title}: i kø siden ${fmt(command.requestedAt)}. GitHub-kjøringen kan bli forsinket.`;
  if(command.status==="running")return `${title}: sender til Android og PWA…`;
  const delivery=command.delivery;
  if(!delivery)return `${title}: ${command.status==="failed"?"utsending feilet":"eldre utsending uten separat leveringsstatus"}.`;
  const android=delivery.android||{};
  const pwa=delivery.pwa||{};
  const parts=[`${title}: ${command.status==="failed"?"utsending feilet helt eller delvis":"utsending behandlet"}.`,
    `Android: ${android.accepted||0}/${android.targets||0} korpskanaler akseptert.`,
    `PWA: ${pwa.accepted||0}/${pwa.targets||0} abonnement akseptert, ${pwa.failed||0} feil, ${pwa.expired||0} utløpt.`];
  if(!pwa.targets)parts.push("Ingen aktive PWA-abonnement nådd.");
  parts.push("Akseptert av push-tjenesten er ikke bekreftelse på visning på telefonen.");
  return parts.join(" ");
}

function loadDashboard(){
  if(dashboardLoad)return dashboardLoad;
  dashboardLoad=fetchDashboard().finally(()=>{dashboardLoad=null});
  return dashboardLoad;
}
async function fetchDashboard(){
  const f=await initFirebase();
  $("lastRefresh").textContent="Oppdaterer…";
  const [health,orgData,release,pushSnap,cacheSnap,runtimeSnap,commandSnap,announcementSnap]=await Promise.all([
    fetchJson(`${DATA_BASE}/health.json`),
    loadOrganizations(),
    fetchJson(`${DATA_BASE}/app-update.json`),
    f.getDocs(f.collection(f.db,"webPushSubscriptions")),
    f.getDoc(f.doc(f.db,"publicConfig","pwa")),
    f.getDoc(f.doc(f.db,"adminRuntime","bridge")),
    f.getDoc(f.doc(f.db,"adminCommands","bridgeSync")),
    f.getDoc(f.doc(f.db,"adminCommands","announcement"))
  ]);

  const runtime=runtimeSnap.exists()?runtimeSnap.data():null;
  const checks=Object.values(runtime?.organizationChecks||{});
  const checkByCode=new Map(checks.map(item=>[item.code,item]));
  currentOrgRows=orgData.rows.map(row=>{
    const check=checkByCode.get(row.code);
    return {
      ...row,
      lastCheckedAt:check?.checkedAt||"",
      error:check?.error||null,
      status:check?.status==="error"?"error":row.status
    };
  }).sort((a,b)=>a.name.localeCompare(b.name,"nb"));
  renderOrgRows();
  const selectedAudience=$("audienceOrg").value;
  $("audienceOrg").replaceChildren(...currentOrgRows.map(row=>{const option=document.createElement('option');option.value=row.code;option.textContent=row.name;return option}));
  if(currentOrgRows.some(row=>row.code===selectedAudience))$("audienceOrg").value=selectedAudience;
  renderSystemHealth(runtime);
  $("lastSuccessfulSync").textContent=fmt(health.lastFullySuccessfulRunAt);
  const issues=currentOrgRows.filter(row=>row.status==='error'||ageMinutes(row.lastCheckedAt)>35);
  $("actionRequired").textContent=issues.length?`${issues.length} korps trenger kontroll. Se korpsstatus nedenfor.`:'Ingen korpsfeil eller forsinkede kontroller registrert.';

  const enabled=[...pushSnap.docs].filter(d=>d.data().enabled!==false).length;
  const totalEvents=currentOrgRows.reduce((sum,row)=>sum+row.eventCount,0);
  const cache=cacheSnap.exists()?cacheSnap.data():{cacheEpoch:0};
  const command=commandSnap.exists()?commandSnap.data():null;

  $("bridgeStatus").textContent=runtime?.status||health.status||"–";
  $("bridgeTime").textContent=fmt(runtime?.lastRunAt||health.lastFullySuccessfulRunAt||health.checkedAt);
  $("corpsCount").textContent=orgData.count;
  $("eventTotal").textContent=totalEvents;
  $("pwaDevices").textContent=pushSnap.size;
  $("pwaActive").textContent=`${enabled} aktive abonnement`;
  $("pendingPush").textContent=runtime?.pendingPushes??health.pendingPushes??"–";
  $("lastPush").textContent=health.lastPushAt?`sist ${fmt(health.lastPushAt)} • ${health.lastPushCount??0}`:"ingen push registrert";
  $("androidVersion").textContent=(release.tagName||"–").replace(/^v/,"");
  $("cacheEpoch").textContent=cache.cacheEpoch??"–";
  $("polledThisRun").textContent=runtime?.polledThisRun??health.polledThisRun??"–";
  renderBridgeSync(command);
  const announcement=announcementSnap.exists()?announcementSnap.data():null;
  $("announcementStatus").textContent=announcementStatusText(announcement);
  $("latestDelivery").textContent=announcementStatusText(announcement);
  $("lastRefresh").textContent="Oppdatert "+new Intl.DateTimeFormat("nb-NO",{timeStyle:"medium"}).format(new Date());
  scheduleDashboardRefresh(["requested","running"].includes(announcement?.status)?announcement:command);
}

$("refreshBtn").onclick=()=>loadDashboard().catch(e=>$("lastRefresh").textContent="Oppdatering feilet: "+e.message);
document.addEventListener('visibilitychange',()=>{
  if(document.hidden){clearTimeout(dashboardTimer);dashboardTimer=null;return}
  if(!$("dashboardView").classList.contains('hidden')){
    loadDashboard().catch(()=>scheduleDashboardRefresh(null));
  }
});
$("verifyConnectionBtn").onclick=async()=>{
  const button=$("verifyConnectionBtn");button.disabled=true;
  $("dispatchStatus").textContent="Kontrollerer tilkoblingen…";
  try {
    const f=await initFirebase();
    $("dispatchStatus").textContent=(await verifyConnection(f.auth.currentUser)).message;
  } finally {button.disabled=false}
};
$("bridgeSyncBtn").onclick=async()=>{
  const f=await initFirebase();
  const button=$("bridgeSyncBtn");
  button.disabled=true;
  button.textContent="Bestiller synk…";
  $("bridgeSyncStatus").className="muted status-warning";
  $("bridgeSyncStatus").textContent="Sender synkforespørsel…";
  try{
    const requestId=(crypto.randomUUID?.()||String(Date.now())+"-"+Math.random().toString(16).slice(2));
    await f.setDoc(f.doc(f.db,"adminCommands","bridgeSync"),{
      action:"bridgeSync",
      status:"requested",
      requestId,
      requestedBy:"superuser",
      requestedAt:f.serverTimestamp()
    });
    const dispatch=await requestDispatch(f.auth.currentUser,"bridgeSync",requestId);
    await loadDashboard();
    $("dispatchStatus").textContent=dispatch.message;
  }catch(error){
    $("bridgeSyncStatus").className="muted status-error";
    $("bridgeSyncStatus").textContent="Kunne ikke bestille synk: "+(error.message||String(error));
    button.disabled=false;
    button.textContent="Prøv Bridge-synk igjen";
  }
};
function previewAnnouncement(testOnly=false){
  const title=$("changelogTitle").value.trim(),body=$("changelogBody").value.trim();
  const audience=testOnly?'test':$("audience").value;
  const organization=audience==='organization'?$("audienceOrg").value:'';
  const subscriptionId=testOnly?$("testDeviceId").value.trim():'';
  if(!title||!body){$("changelogMessage").textContent='Fyll inn tittel og endringer.';return;}
  if(testOnly&&!/^[a-f0-9]{64}$/.test(subscriptionId)){$("changelogMessage").textContent='Lim inn enhetskoden fra Varsler i nettappen på din egen testenhet.';return;}
  if(audience==='organization'&&!organization){$("changelogMessage").textContent='Velg et korps.';return;}
  approvedDraft={title,body,audience,organization,subscriptionId,sendPush:testOnly||$("changelogPush").checked};
  $("previewTitle").textContent=title;
  $("previewBody").textContent=body;
  $("previewAudience").textContent=testOnly?'Kun valgt PWA-testenhet. Ingen offentlig endringslogg.':
    'Endringsloggen er synlig for alle. '+(approvedDraft.sendPush?(audience==='all'?'Push til alle.':'Push bare til '+$("audienceOrg").selectedOptions[0].textContent+'.'):'Ingen push.');
  $("previewDialog").showModal();
}
$("publishChangelogBtn").onclick=()=>previewAnnouncement(false);
$("testPushBtn").onclick=()=>previewAnnouncement(true);
$("cancelPreview").onclick=()=>{$("previewDialog").close();approvedDraft=null;};
$("confirmPreview").onclick=async()=>{
  if(!approvedDraft)return;
  const draft=approvedDraft;approvedDraft=null;$("previewDialog").close();
  await publishAnnouncement(draft);
};
async function publishAnnouncement(draft){
  const {title,body,sendPush,audience,organization,subscriptionId}=draft;
  const testOnly=audience==='test';
  const message=$("changelogMessage");
  if(!title||!body){message.textContent="Fyll inn tittel og endringer.";return}
  const f=await initFirebase();
  const id=new Date().toISOString().replace(/[:.]/g,"-");
  $("publishChangelogBtn").disabled=true;$("testPushBtn").disabled=true;
  message.textContent="Publiserer…";
  try{
    const payload={id,title,body,publishedAt:f.serverTimestamp(),publishedBy:"superuser",sendPush};
    await f.runTransaction(f.db,async transaction=>{
      const commandRef=f.doc(f.db,"adminCommands","announcement");
      if(sendPush){
        const existing=await transaction.get(commandRef);
        if(existing.exists() && ["requested","running"].includes(existing.data().status)){
          throw new Error("En utsending venter fortsatt. Vent til den er ferdig før du sender en ny.");
        }
      }
      if(!testOnly){
        transaction.set(f.doc(f.db,"changelog",id),payload);
        transaction.set(f.doc(f.db,"publicConfig","changelog"),{latestId:id,title,body,updatedAt:f.serverTimestamp()});
      }
      if(sendPush)transaction.set(commandRef,{
        action:"announcement",status:"requested",requestId:id,title,
        body:body.length>180?body.slice(0,177)+"…":body,
        topic:"kova_all_users",requestedBy:"superuser",requestedAt:f.serverTimestamp(),
        source:"changelog",audience,organization,subscriptionId
      });
    });
    const dispatch=sendPush?await requestDispatch(f.auth.currentUser,"announcement",id):null;
    message.textContent=testOnly?"Test bestilt for én PWA-enhet. "+dispatch.message:sendPush
      ? "Endringsloggen er publisert. "+dispatch.message
      : "Endringsloggen er publisert uten push.";
    await loadDashboard();
    $("changelogTitle").value="";$("changelogBody").value="";
  }catch(error){message.textContent="Publisering feilet: "+(error.message||String(error))}
  finally{$("publishChangelogBtn").disabled=false;$("testPushBtn").disabled=false}
};
$("orgSearch").addEventListener("input",renderOrgRows);
$("signOutBtn").onclick=async()=>{
  const f=await initFirebase();await f.signOut(f.auth);show("loginView");
};
$("cacheRefreshBtn").onclick=async()=>{
  const f=await initFirebase();
  const ref=f.doc(f.db,"publicConfig","pwa");
  const snap=await f.getDoc(ref);
  const current=snap.exists()?(snap.data().cacheEpoch||0):0;
  $("cacheRefreshBtn").disabled=true;
  $("cacheMessage").textContent="Publiserer ny cache-generasjon…";
  try{
    await f.setDoc(ref,{cacheEpoch:current+1,updatedAt:f.serverTimestamp()});
    $("cacheMessage").textContent=`Ny PWA-cachegenerasjon ${current+1} er publisert.`;
    await loadDashboard();
  }catch(error){
    $("cacheMessage").textContent="Cache-refresh feilet: "+(error.message||String(error));
  }finally{
    $("cacheRefreshBtn").disabled=false;
  }
};

(async()=>{
  try{
    const f=await initFirebase();
    f.onAuthStateChanged(f.auth,handleUser);
  }catch(error){
    err("loginError","Adminpanelet kunne ikke starte: "+(error.message||String(error)));
  }
})();
