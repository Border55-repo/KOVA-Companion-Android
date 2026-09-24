const DATA_BASE = "https://raw.githubusercontent.com/Border55-repo/KOVA-Companion-Android/main/bridge/data";
const WEBPUSH_CONFIG_URL = `${DATA_BASE}/webpush-config.json`;
const FIREBASE_CONFIG_URL = "./firebase-web-config.json";
let firestoreClientPromise = null;

const state = {
  orgs: [],
  org: localStorage.getItem("kova.pwa.org") || "UllensakerRKH",
  events: [],
  view: "all",
  search: "",
  type: "",
  favorites: new Set(JSON.parse(localStorage.getItem("kova.pwa.favorites") || "[]")),
  favoriteMeta: JSON.parse(localStorage.getItem("kova.pwa.favoriteMeta") || "{}"),
  notes: JSON.parse(localStorage.getItem("kova.pwa.notes") || "{}"),
  reminders: JSON.parse(localStorage.getItem("kova.pwa.reminders") || "{}"),
  favoriteEvents: [],
  displayLimit: 20,
  followed: new Set(JSON.parse(localStorage.getItem("kova.pwa.followed") || '["UllensakerRKH"]')),
  favoriteOrgs: new Set(JSON.parse(localStorage.getItem("kova.pwa.favoriteOrgs") || "[]")),
  notificationKinds: new Set(JSON.parse(localStorage.getItem("kova.pwa.notificationKinds") || '["added","changed","removed","announcement"]')),
  disabledNotificationTypes: new Set(JSON.parse(localStorage.getItem("kova.pwa.disabledNotificationTypes") || "[]")),
  quietEnabled: localStorage.getItem("kova.pwa.quietEnabled")==="1",
  quietStartHour: Number(localStorage.getItem("kova.pwa.quietStartHour")||22),
  quietEndHour: Number(localStorage.getItem("kova.pwa.quietEndHour")||7),
  orgIndex: new Map(),
  bridgeApiVersion: "1.x",
  bridgeCapabilities: {},
  selected: null,
};

const $ = id => document.getElementById(id);
const orgSelect = $("orgSelect");
const typeSelect = $("typeSelect");
const eventsEl = $("events");
const emptyEl = $("empty");
const statusText = $("statusText");
const searchInput = $("searchInput");
const template = $("eventTemplate");

function isIOS(){return /iphone|ipad|ipod/i.test(navigator.userAgent)}
function isStandalone(){return window.matchMedia("(display-mode: standalone)").matches || navigator.standalone === true}
function eventKey(event){return (event.orgCode || state.org) + "|" + event.id}
function displayTime(value=""){
  const t=value.trim();
  if(!t)return "Tid ikke oppgitt";
  if(t.startsWith("->"))return "Til " + (t.match(/\d{1,2}:\d{2}/)?.[0] || t);
  return t;
}
function saveSet(key,set){localStorage.setItem(key,JSON.stringify([...set]))}
function saveFavoriteMeta(){localStorage.setItem("kova.pwa.favoriteMeta",JSON.stringify(state.favoriteMeta))}
function saveNotes(){localStorage.setItem("kova.pwa.notes",JSON.stringify(state.notes))}
function saveReminders(){localStorage.setItem("kova.pwa.reminders",JSON.stringify(state.reminders))}
function saveNotificationPrefs(){
  saveSet("kova.pwa.notificationKinds",state.notificationKinds);
  saveSet("kova.pwa.disabledNotificationTypes",state.disabledNotificationTypes);
  localStorage.setItem("kova.pwa.quietEnabled",state.quietEnabled?"1":"0");
  localStorage.setItem("kova.pwa.quietStartHour",String(state.quietStartHour));
  localStorage.setItem("kova.pwa.quietEndHour",String(state.quietEndHour));
}
function timeHour(value,fallback){
  const hour=Number(String(value||"").split(":")[0]);
  return Number.isFinite(hour)?Math.max(0,Math.min(23,hour)):fallback;
}
function normalizeText(value=""){return String(value).trim().toLowerCase().replace(/\s+/g," ")}
function semanticKey(event){return normalizeText(event.type)+"|"+normalizeText(event.description)}
function noteFor(event){return state.notes[eventKey(event)]||""}
function saveNote(event,value){
  const key=eventKey(event);
  if(value.trim())state.notes[key]=value;
  else delete state.notes[key];
  saveNotes();
}
function favoriteMetaFor(event){
  const key=eventKey(event);
  const existing=state.favoriteMeta[key]||{};
  return {
    orgCode:event.orgCode||state.org,
    eventId:event.id,
    semanticKey:semanticKey(event),
    anchorDate:existing.anchorDate||event.dateIso||"",
    dateIso:event.dateIso||"",
    time:event.time||"",
    description:event.description||"",
    type:event.type||""
  };
}
function rememberFavoriteEvent(event){
  const key=eventKey(event);
  state.favoriteMeta[key]=favoriteMetaFor(event);
  saveFavoriteMeta();
}
function reminderEntryFor(event){
  const raw=state.reminders[eventKey(event)];
  if(!raw)return null;
  if(typeof raw==="number")return {leadMinutes:raw,anchorDate:event.dateIso||""};
  return raw;
}
function reminderMinutesFor(event){return Number(reminderEntryFor(event)?.leadMinutes||0)}
function reminderLabel(minutes){
  if(!minutes)return "Ingen";
  if(minutes%(24*60)===0){
    const days=minutes/(24*60); return days===1?"1 dag":days+" dager";
  }
  if(minutes%60===0){
    const hours=minutes/60; return hours===1?"1 time":hours+" timer";
  }
  return minutes+" minutter";
}
function dateDistanceDays(a,b){
  if(!a||!b)return Infinity;
  const x=new Date(a+"T00:00:00"),y=new Date(b+"T00:00:00");
  return Math.abs(x-y)/86400000;
}
function migrateFavorite(oldKey,event){
  const newKey=eventKey(event);
  if(oldKey===newKey)return false;
  const oldMeta=state.favoriteMeta[oldKey]||{};
  state.favorites.delete(oldKey);
  state.favorites.add(newKey);
  if(Object.prototype.hasOwnProperty.call(state.notes,oldKey)){
    state.notes[newKey]=state.notes[oldKey];
    delete state.notes[oldKey];
  }
  if(Object.prototype.hasOwnProperty.call(state.reminders,oldKey)){
    state.reminders[newKey]=state.reminders[oldKey];
    delete state.reminders[oldKey];
  }
  delete state.favoriteMeta[oldKey];
  state.favoriteMeta[newKey]={
    ...favoriteMetaFor(event),
    anchorDate:oldMeta.anchorDate||oldMeta.dateIso||event.dateIso||""
  };
  saveSet("kova.pwa.favorites",state.favorites);
  saveFavoriteMeta(); saveNotes(); saveReminders();
  return true;
}
function reconcileFavorites(code,events){
  const byKey=new Map(events.map(event=>[eventKey(event),event]));
  let changed=false;
  for(const key of [...state.favorites].filter(item=>item.startsWith(code+"|"))){
    const exact=byKey.get(key);
    if(exact){
      const before=JSON.stringify(state.favoriteMeta[key]||{});
      state.favoriteMeta[key]=favoriteMetaFor(exact);
      if(JSON.stringify(state.favoriteMeta[key])!==before)changed=true;
      continue;
    }
    const meta=state.favoriteMeta[key];
    if(!meta?.semanticKey)continue;
    const candidates=events
      .filter(event=>semanticKey(event)===meta.semanticKey)
      .map(event=>({event,distance:dateDistanceDays(meta.dateIso||meta.anchorDate,event.dateIso)}))
      .sort((a,b)=>a.distance-b.distance);
    if(candidates.length===1 || (candidates.length>1 && candidates[0].distance<=14)){
      changed=migrateFavorite(key,candidates[0].event)||changed;
    }
  }
  if(changed)saveFavoriteMeta();
}
function dateInRange(dateIso,days){
  if(!dateIso)return false;
  const date=new Date(dateIso+"T00:00:00");
  const today=new Date(); today.setHours(0,0,0,0);
  const end=new Date(today); end.setDate(end.getDate()+days);
  return date>=today && date<=end;
}
function orgName(code){return state.orgs.find(o=>o.code===code)?.name || code}
function updateConnection(){
  $("connectionText").textContent=navigator.onLine ? "På nett" : "Frakoblet – viser cache hvis tilgjengelig";
}

function base64UrlToUint8Array(value){
  const padding="=".repeat((4-value.length%4)%4);
  const base64=(value+padding).replace(/-/g,"+").replace(/_/g,"/");
  const raw=atob(base64);
  return Uint8Array.from([...raw].map(char=>char.charCodeAt(0)));
}

async function endpointId(endpoint){
  const bytes=new TextEncoder().encode(endpoint);
  const hash=await crypto.subtle.digest("SHA-256",bytes);
  return [...new Uint8Array(hash)].map(byte=>byte.toString(16).padStart(2,"0")).join("");
}
function reminderToken(){
  let token=localStorage.getItem("kova.pwa.reminderToken");
  if(token)return token;
  const bytes=new Uint8Array(32);
  crypto.getRandomValues(bytes);
  token=[...bytes].map(value=>value.toString(16).padStart(2,"0")).join("");
  localStorage.setItem("kova.pwa.reminderToken",token);
  return token;
}

async function firestoreClient(){
  if(!firestoreClientPromise){
    firestoreClientPromise=(async()=>{
      const [appModule,firestoreModule,config]=await Promise.all([
        import("https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js"),
        import("https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js"),
        fetch(FIREBASE_CONFIG_URL,{cache:"no-store"}).then(response=>{
          if(!response.ok)throw new Error("Kunne ikke hente Firebase Web-konfigurasjon");
          return response.json();
        })
      ]);
      const firebaseApp=appModule.getApps().length ? appModule.getApps()[0] : appModule.initializeApp(config);
      return {
        db:firestoreModule.getFirestore(firebaseApp),
        doc:firestoreModule.doc,
        setDoc:firestoreModule.setDoc,
        serverTimestamp:firestoreModule.serverTimestamp,
        getDoc:firestoreModule.getDoc
      };
    })().catch(error=>{
      firestoreClientPromise=null;
      throw error;
    });
  }
  return firestoreClientPromise;
}

function pushSupported(){
  return "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;
}

async function currentPushSubscription(){
  if(!pushSupported())return null;
  const registration=await navigator.serviceWorker.ready;
  return registration.pushManager.getSubscription();
}

async function savePushSubscription(subscription,enabled=true){
  const json=subscription.toJSON();
  const endpoint=json.endpoint||subscription.endpoint;
  const keys=json.keys||{};
  if(!endpoint||!keys.p256dh||!keys.auth)throw new Error("Ufullstendig Web Push-abonnement");

  const id=await endpointId(endpoint);
  const organizations=[...state.favoriteOrgs];

  try{
    const firebase=await firestoreClient();
    await firebase.setDoc(
      firebase.doc(firebase.db,"webPushSubscriptions",id),
      {
        endpoint,
        p256dh:keys.p256dh,
        auth:keys.auth,
        organizations:organizations.slice(0,100),
        enabled,
        platform:"pwa",
        reminderToken:reminderToken(),
        notificationKinds:[...new Set([...state.notificationKinds,"announcement"])],
        disabledEventTypes:[...state.disabledNotificationTypes].slice(0,100),
        quietHoursEnabled:state.quietEnabled,
        quietStartHour:state.quietStartHour,
        quietEndHour:state.quietEndHour,
        updatedAt:firebase.serverTimestamp()
      },
      {merge:false}
    );
    localStorage.setItem("kova.pwa.pushRegisteredAt",new Date().toISOString());
    localStorage.removeItem("kova.pwa.pushLastError");
    syncAllReminders().catch(error=>console.warn("Kunne ikke synkronisere vaktpåminnelser",error));
  }catch(error){
    localStorage.removeItem("kova.pwa.pushRegisteredAt");
    localStorage.setItem("kova.pwa.pushLastError",error?.message||String(error));
    throw new Error("Kunne ikke registrere varsler mot KOVA-backend: "+(error?.message||String(error)));
  }
}

async function reminderDocumentId(subscriptionId,event){
  const meta=state.favoriteMeta[eventKey(event)]||favoriteMetaFor(event);
  return endpointId(
    subscriptionId+"|"+(event.orgCode||state.org)+"|"+semanticKey(event)+"|"+(meta.anchorDate||event.dateIso||"")
  );
}
async function syncReminderBackend(event,leadMinutes,enabled=true){
  const subscription=await currentPushSubscription();
  if(!subscription || Notification.permission!=="granted")return false;
  if(enabled && !state.favoriteOrgs.has(event.orgCode||state.org))return false;
  const json=subscription.toJSON();
  const endpoint=json.endpoint||subscription.endpoint;
  if(!endpoint)return false;
  const subscriptionId=await endpointId(endpoint);
  const reminderId=await reminderDocumentId(subscriptionId,event);
  const firebase=await firestoreClient();
  await firebase.setDoc(
    firebase.doc(firebase.db,"webPushReminders",reminderId),
    {
      subscriptionId,
      reminderToken:reminderToken(),
      organization:event.orgCode||state.org,
      eventId:event.id,
      semanticKey:semanticKey(event),
      dateIso:event.dateIso||"",
      time:event.time||"",
      description:event.description||"",
      leadMinutes:Math.max(5,Number(leadMinutes)||5),
      enabled:Boolean(enabled),
      updatedAt:firebase.serverTimestamp()
    },
    {merge:false}
  );
  return true;
}
async function saveReminder(event,minutes){
  const key=eventKey(event);
  const current=reminderEntryFor(event);
  if(minutes>0){
    state.reminders[key]={
      leadMinutes:minutes,
      anchorDate:current?.anchorDate||state.favoriteMeta[key]?.anchorDate||event.dateIso||""
    };
    saveReminders();
    return syncReminderBackend(event,minutes,true).catch(()=>false);
  }
  if(current){
    await syncReminderBackend(event,current.leadMinutes,false).catch(()=>false);
  }
  delete state.reminders[key];
  saveReminders();
  return true;
}
async function syncAllReminders(){
  if(Notification.permission!=="granted")return;
  const all=[...state.favoriteEvents,...state.events];
  const seen=new Set();
  for(const event of all){
    const key=eventKey(event);
    if(seen.has(key))continue;
    seen.add(key);
    const minutes=reminderMinutesFor(event);
    if(minutes>0 && state.favorites.has(key)){
      await syncReminderBackend(event,minutes,true);
    }
  }
}

async function syncPushOrganizations(){
  try{
    const subscription=await currentPushSubscription();
    if(subscription && Notification.permission==="granted"){
      await savePushSubscription(subscription,true);
    }
  }catch(error){
    console.warn("Kunne ikke synkronisere push-korps",error);
  }
}

async function checkRemoteCacheEpoch(){
  try{
    const f=await firestoreClient();
    const snap=await f.getDoc(f.doc(f.db,"publicConfig","pwa"));
    if(!snap.exists())return false;
    const epoch=Number(snap.data().cacheEpoch||0);
    if(!epoch)return false;
    const previous=Number(localStorage.getItem("kova.pwa.cacheEpoch")||0);
    if(!previous){
      localStorage.setItem("kova.pwa.cacheEpoch",String(epoch));
      return false;
    }
    if(epoch<=previous)return false;

    localStorage.setItem("kova.pwa.cacheEpoch",String(epoch));
    const keys=await caches.keys();
    await Promise.all(keys.filter(k=>k.startsWith("kova-companion-pwa-")).map(k=>caches.delete(k)));
    const registration=await navigator.serviceWorker.getRegistration();
    try{await registration?.update()}catch{}
    sessionStorage.setItem("kova.pwa.remoteCacheEpoch",String(epoch));
    location.reload();
    return true;
  }catch(error){
    console.warn("Kunne ikke sjekke fjernstyrt PWA-cache",error);
    return false;
  }
}

async function repairPushRegistration(){
  if(!pushSupported()) return false;
  if(isIOS() && !isStandalone()) return false;
  if(Notification.permission!=="granted") return false;

  const subscription=await currentPushSubscription();
  if(!subscription) return false;

  try{
    await savePushSubscription(subscription,true);
    return true;
  }catch(error){
    console.warn("Automatisk backend-registrering av push feilet",error);
    localStorage.removeItem("kova.pwa.pushRegisteredAt");
    return false;
  }
}

let repairingPush=false;
async function repairPushOnResume(){
  if(repairingPush) return;
  repairingPush=true;
  try{
    await repairPushRegistration();
    await refreshNotificationUi();
  }finally{
    repairingPush=false;
  }
}

async function refreshNotificationUi(){
  const button=$("notificationBtn");
  const status=$("notificationStatus");
  const hint=$("notificationHint");

  if(!pushSupported()){
    status.textContent="Ikke støttet på denne enheten";
    hint.textContent="";
    button.disabled=true;
    return;
  }

  if(isIOS()&&!isStandalone()){
    status.textContent="Installer Kova Companion først";
    hint.textContent="På iPhone fungerer varsler etter at PWA-en er lagt på Hjem-skjermen.";
    button.textContent="Installer først";
    button.disabled=true;
    return;
  }

  button.disabled=false;
  const subscription=await currentPushSubscription();
  const enabled=Notification.permission==="granted" && !!subscription;
  const registeredAt=localStorage.getItem("kova.pwa.pushRegisteredAt");
  const backendRegistered=enabled && !!registeredAt;
  status.textContent=backendRegistered
    ? "Varsler er på – backend registrert"
    : enabled
      ? "Varsler er på lokalt – registrering mangler"
      : (Notification.permission==="denied" ? "Varsler er blokkert" : "Varsler er av");
  hint.textContent=backendRegistered
    ? `Varsler fra ${state.favoriteOrgs.size} favorittkorps • registrert ${new Intl.DateTimeFormat("nb-NO",{dateStyle:"short",timeStyle:"short"}).format(new Date(registeredAt))}`
    : enabled
      ? "Trykk Registrer på nytt for å koble enheten til KOVA Bridge."
      : "Ny, endret og fjernet aktivitet";
  if(!backendRegistered && localStorage.getItem("kova.pwa.pushLastError")){
    hint.textContent="Siste registreringsfeil: "+localStorage.getItem("kova.pwa.pushLastError");
  }
  button.textContent=backendRegistered ? "Slå av varsler" : (enabled ? "Registrer på nytt" : "Aktiver varsler");
  button.classList.toggle("active",backendRegistered);
}

async function enableNotifications(){
  if(isIOS()&&!isStandalone())return;
  const permission=await Notification.requestPermission();
  if(permission!=="granted"){
    await refreshNotificationUi();
    return;
  }
  const config=await fetchJson(WEBPUSH_CONFIG_URL);
  const registration=await navigator.serviceWorker.ready;
  let subscription=await registration.pushManager.getSubscription();
  if(!subscription){
    subscription=await registration.pushManager.subscribe({
      userVisibleOnly:true,
      applicationServerKey:base64UrlToUint8Array(config.publicKey)
    });
  }
  await savePushSubscription(subscription,true);
  await refreshNotificationUi();
}

async function disableNotifications(){
  const subscription=await currentPushSubscription();
  if(subscription){
    await savePushSubscription(subscription,false);
    await subscription.unsubscribe();
  }
  localStorage.removeItem("kova.pwa.pushRegisteredAt");
  await refreshNotificationUi();
}

async function toggleNotifications(){
  try{
    // iOS requires the permission prompt directly in the tap handler.
    if(Notification.permission!=="granted"){
      await enableNotifications();
      return;
    }
    const subscription=await currentPushSubscription();
    const enabled=Notification.permission==="granted" && !!subscription;
    const backendRegistered=enabled && !!localStorage.getItem("kova.pwa.pushRegisteredAt");
    if(backendRegistered){
      await disableNotifications();
    }else if(enabled){
      await savePushSubscription(subscription,true);
      await refreshNotificationUi();
    }else{
      await enableNotifications();
    }
  }catch(error){
    $("notificationStatus").textContent="Varseloppsett feilet";
    $("notificationHint").textContent=error.message||String(error);
  }
}
function renderOrgOptions(filter=""){
  const q=filter.trim().toLowerCase();
  const current=state.org;
  const rows=state.orgs
    .filter(org=>!q || org.name.toLowerCase().includes(q) || org.code.toLowerCase().includes(q))
    .sort((a,b)=>{
      const af=state.favoriteOrgs.has(a.code)?0:1;
      const bf=state.favoriteOrgs.has(b.code)?0:1;
      return af-bf || a.name.localeCompare(b.name,"nb");
    });
  orgSelect.innerHTML=rows.map(org=>{
    const star=state.favoriteOrgs.has(org.code)?"★ ":"";
    return `<option value="${escapeHtml(org.code)}">${star}${escapeHtml(org.name)}</option>`;
  }).join("");
  if(rows.some(org=>org.code===current))orgSelect.value=current;
}
function updateFavoriteOrgUi(){
  const favorite=state.favoriteOrgs.has(state.org);
  $("favoriteOrgBtn").textContent=favorite?"★ Favoritt":"☆ Favoritt";
  $("favoriteOrgBtn").classList.toggle("active",favorite);
}
function updateDataQuality(){
  const row=state.orgIndex.get(state.org);
  if(!row){
    $("dataQuality").textContent="Datakvalitet: status ikke tilgjengelig ennå.";
    return;
  }
  const status=row.status==="ok"?"OK":(row.status||"ukjent");
  $("dataQuality").textContent=[
    "Datakvalitet: "+status,
    Number.isFinite(Number(row.eventCount))?row.eventCount+" aktiviteter":"",
    row.updatedAt?"oppdatert "+formatUpdated(row.updatedAt):""
  ].filter(Boolean).join(" • ");
}
function renderNotificationTypeSettings(){
  const types=[...new Set(state.events.map(event=>event.type).filter(Boolean))].sort((a,b)=>a.localeCompare(b,"nb"));
  const container=$("notificationTypes");
  container.innerHTML="";
  if(!types.length){
    container.textContent="Aktivitetstyper vises etter første synk.";
    return;
  }
  for(const type of types){
    const label=document.createElement("label");
    const input=document.createElement("input");
    input.type="checkbox";
    input.checked=!state.disabledNotificationTypes.has(type);
    input.onchange=async()=>{
      input.checked?state.disabledNotificationTypes.delete(type):state.disabledNotificationTypes.add(type);
      saveNotificationPrefs();
      await syncPushOrganizations();
    };
    label.append(input,document.createTextNode(type));
    container.appendChild(label);
  }
}
function updateNotificationSettingsUi(){
  $("notifyAdded").checked=state.notificationKinds.has("added");
  $("notifyChanged").checked=state.notificationKinds.has("changed");
  $("notifyRemoved").checked=state.notificationKinds.has("removed");
  $("quietEnabled").checked=state.quietEnabled;
  $("quietStart").value=String(state.quietStartHour).padStart(2,"0")+":00";
  $("quietEnd").value=String(state.quietEndHour).padStart(2,"0")+":00";
  renderNotificationTypeSettings();
}

function updateFollowUi(){
  const followed=state.followed.has(state.org);
  $("followBtn").textContent=followed ? "★ Følger" : "☆ Følg";
  $("followBtn").classList.toggle("active",followed);
  $("followSummary").textContent=state.followed.size ? `${state.followed.size} korps fulgt` : "Ingen korps fulgt";
  updateFavoriteOrgUi();
  updateDataQuality();
}
function updateTypes(){
  const current=typeSelect.value;
  const types=[...new Set(state.events.map(e=>e.type).filter(Boolean))].sort((a,b)=>a.localeCompare(b,"nb"));
  typeSelect.innerHTML='<option value="">Alle typer</option>'+types.map(t=>`<option value="${escapeHtml(t)}">${escapeHtml(t)}</option>`).join("");
  state.type=types.includes(current) ? current : "";
  typeSelect.value=state.type;
  renderNotificationTypeSettings();
}
function upcomingEvents(){
  return state.events
    .filter(event=>event.dateIso && dateInRange(event.dateIso,3650))
    .sort((a,b)=>(a.dateIso+(eventTime(a)||"99:99")).localeCompare(b.dateIso+(eventTime(b)||"99:99")));
}

function sortUpcoming(events){
  return events
    .filter(event=>event.dateIso && dateInRange(event.dateIso,3650))
    .sort((a,b)=>(a.dateIso+(eventTime(a)||"99:99")).localeCompare(b.dateIso+(eventTime(b)||"99:99")));
}
async function loadFavoriteEvents(){
  const codes=[...new Set([...state.favorites].map(key=>key.split("|")[0]).filter(Boolean))];
  if(!codes.length)return [];
  const results=await Promise.allSettled(codes.map(loadOneOrg));
  return sortUpcoming(
    results
      .filter(result=>result.status==="fulfilled")
      .flatMap(result=>result.value.events)
      .filter(event=>state.favorites.has(eventKey(event)))
  );
}
async function refreshFavoriteDashboard(){
  const card=$("myShiftsCard");
  if(!state.favorites.size){
    state.favoriteEvents=[];
    card.classList.add("hidden");
    return;
  }
  card.classList.remove("hidden");
  try{
    state.favoriteEvents=await loadFavoriteEvents();
    const count=state.favoriteEvents.length;
    $("myShiftsCount").textContent=`${count} ${count===1?"kommende":"kommende"}`;
    const next=state.favoriteEvents[0]||null;
    $("myShiftsNextBtn").classList.toggle("hidden",!next);
    $("myShiftsEmpty").classList.toggle("hidden",!!next);
    if(next){
      $("myShiftsTitle").textContent=next.description||"KOVA-aktivitet";
      $("myShiftsMeta").textContent=[
        next.dateLabel,
        displayTime(next.time),
        next.orgName||orgName(next.orgCode)
      ].filter(Boolean).join(" • ");
      $("myShiftsNextBtn").onclick=()=>openDetail(next);
    }
  }catch(error){
    $("myShiftsCount").textContent="Kunne ikke oppdatere";
    $("myShiftsNextBtn").classList.add("hidden");
    $("myShiftsEmpty").classList.remove("hidden");
  }
}

function renderMiniList(containerId,events){
  const container=$(containerId);
  container.innerHTML="";
  for(const event of events.slice(0,4)){
    const button=document.createElement("button");
    button.className="mini-event";
    const title=document.createElement("strong");
    title.textContent=event.description||"KOVA-aktivitet";
    const meta=document.createElement("span");
    meta.textContent=[event.dateLabel,displayTime(event.time)].filter(Boolean).join(" • ");
    button.append(title,meta);
    button.onclick=()=>openDetail(event);
    container.appendChild(button);
  }
}

function renderEverydayDashboard(){
  const upcoming=upcomingEvents();
  const next=upcoming[0]||null;
  const nextCard=$("nextShiftCard");
  nextCard.classList.toggle("hidden",!next);
  if(next){
    $("nextShiftTitle").textContent=next.description||"KOVA-aktivitet";
    $("nextShiftMeta").textContent=[
      next.dateLabel,
      displayTime(next.time),
      next.orgName||orgName(next.orgCode)
    ].filter(Boolean).join(" • ");
    $("nextShiftButton").onclick=()=>openDetail(next);
  }

  const week=upcoming.filter(event=>dateInRange(event.dateIso,7));
  const later=upcoming.filter(event=>!dateInRange(event.dateIso,7) && dateInRange(event.dateIso,30));
  $("weekCount").textContent=`${week.length} ${week.length===1?"vakt":"vakter"}`;
  $("laterCount").textContent=`${later.length} ${later.length===1?"vakt":"vakter"}`;
  renderMiniList("weekEvents",week);
  renderMiniList("laterEvents",later);
}

function filteredEvents(){
  const q=state.search.trim().toLowerCase();
  return state.events.filter(event=>{
    if(state.view==="favorites" && !state.favorites.has(eventKey(event)))return false;
    if(state.view==="week" && !dateInRange(event.dateIso,7))return false;
    if(state.view==="month" && !dateInRange(event.dateIso,30))return false;
    if(state.view==="all" && event.dateIso && !dateInRange(event.dateIso,3650))return false;
    if(state.type && event.type!==state.type)return false;
    return !q || [event.description,event.type,event.dateLabel,event.time,event.orgName]
      .some(v=>(v||"").toLowerCase().includes(q));
  }).sort((a,b)=>(a.dateIso+a.time).localeCompare(b.dateIso+b.time));
}
function render(){
  eventsEl.innerHTML="";
  const all=filteredEvents();
  const list=state.view==="favorites" ? all.slice(0,state.displayLimit) : all;
  $("eventCount").textContent=all.length;
  emptyEl.classList.toggle("hidden",all.length!==0);
  $("loadMoreBtn").classList.toggle("hidden",state.view!=="favorites"||list.length>=all.length);
  $("loadMoreBtn").textContent=list.length<all.length ? `Vis flere (${all.length-list.length} igjen)` : "Vis flere";
  renderEverydayDashboard();
  const multi=state.view==="followed"||state.view==="favorites";
  let lastGroup="";

  for(const event of list){
    if(state.view==="favorites"){
      const date=new Date((event.dateIso||"")+"T00:00:00");
      const group=Number.isNaN(date.getTime())
        ? "Uten dato"
        : new Intl.DateTimeFormat("nb-NO",{month:"long",year:"numeric"}).format(date);
      if(group!==lastGroup){
        const heading=document.createElement("div");
        heading.className="event-group";
        heading.textContent=group;
        eventsEl.appendChild(heading);
        lastGroup=group;
      }
    }
    const node=template.content.cloneNode(true);
    node.querySelector(".type").textContent=event.type||"Aktivitet";
    node.querySelector(".description").textContent=event.description||"KOVA-aktivitet";
    node.querySelector(".meta").textContent=[event.dateLabel,displayTime(event.time)].filter(Boolean).join(" • ");
    const chip=node.querySelector(".org-chip");
    chip.textContent=event.orgName||orgName(event.orgCode);
    chip.classList.toggle("hidden",!multi);
    const fav=node.querySelector(".favorite");
    fav.textContent=state.favorites.has(eventKey(event)) ? "★" : "☆";
    fav.onclick=async()=>{await toggleFavorite(event)};
    node.querySelector(".event-main").onclick=()=>openDetail(event);
    eventsEl.appendChild(node);
  }
}
async function toggleFavorite(event){
  const key=eventKey(event);
  if(state.favorites.has(key)){
    const current=reminderEntryFor(event);
    if(current)await syncReminderBackend(event,current.leadMinutes,false).catch(()=>false);
    state.favorites.delete(key);
    delete state.favoriteMeta[key];
    delete state.reminders[key];
    saveSet("kova.pwa.favorites",state.favorites);
    saveFavoriteMeta();
    saveReminders();
  }else{
    state.favorites.add(key);
    rememberFavoriteEvent(event);
    saveSet("kova.pwa.favorites",state.favorites);
  }
  updateDialogFavorite();
  render();
  await refreshFavoriteDashboard().catch(()=>{});
}
function extractContact(event){
  const text=event.contact||event.description||"";
  const email=text.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)?.[0]||"";
  const phone=text.match(/(?:\+47\s*)?(?:\d[\s-]?){8}/)?.[0]?.trim()||"";
  return email||phone;
}
function extractLocation(event){
  if(event.location)return event.location;
  const description=String(event.description||"");
  const parts=description.split(",").map(part=>part.trim()).filter(Boolean);
  if(parts.length>1){
    const candidate=parts.at(-1);
    if(candidate.length>=4 && candidate.length<=90 && !/^\d{1,2}:\d{2}/.test(candidate)){
      return candidate;
    }
  }
  return "";
}
function renderDetailExtra(event){
  const container=$("detailExtra");
  container.innerHTML="";
  const location=extractLocation(event);
  const contact=extractContact(event);
  if(location){
    const row=document.createElement("div");
    const label=document.createElement("strong");
    label.textContent="Sted: ";
    const link=document.createElement("a");
    link.href="https://www.google.com/maps/search/?api=1&query="+encodeURIComponent(location);
    link.target="_blank";
    link.rel="noopener";
    link.textContent=location;
    row.append(label,link);
    container.appendChild(row);
  }
  if(contact){
    const row=document.createElement("div");
    const label=document.createElement("strong");
    label.textContent="Kontakt: ";
    const value=document.createElement("span");
    value.textContent=contact;
    row.append(label,value);
    container.appendChild(row);
  }
  if(!location&&!contact){
    const row=document.createElement("span");
    row.className="muted";
    row.textContent="KOVA har ikke strukturert sted eller kontaktinformasjon for denne aktiviteten.";
    container.appendChild(row);
  }
  const change=event.changeSummary||"";
  $("detailChange").textContent=change;
  $("detailChange").classList.toggle("hidden",!change);
}
function openDetail(event){
  state.selected=event;
  $("detailType").textContent=event.type||"Aktivitet";
  $("detailTitle").textContent=event.description||"KOVA-aktivitet";
  $("detailMeta").textContent=[event.dateLabel,displayTime(event.time)].filter(Boolean).join(" • ");
  $("detailOrg").textContent=event.orgName||orgName(event.orgCode);
  $("detailNote").value=noteFor(event);
  $("detailKovaLink").href=event.sourceUrl||"https://www.kova.no/";
  renderDetailExtra(event);
  updateDialogFavorite();
  updateReminderUi();
  $("detailDialog").showModal();
}
function updateDialogFavorite(){
  if(!state.selected)return;
  const favorite=state.favorites.has(eventKey(state.selected));
  $("favoriteDialogBtn").textContent=favorite ? "★ Fjern favoritt" : "☆ Legg til favoritt";
  $("reminderSelect").disabled=!favorite||!eventTime(state.selected);
}
function updateReminderUi(message=""){
  if(!state.selected)return;
  const favorite=state.favorites.has(eventKey(state.selected));
  const hasTime=!!eventTime(state.selected);
  $("reminderSelect").value=String(reminderMinutesFor(state.selected)||0);
  if(message){
    $("reminderHint").textContent=message;
  }else if(!favorite){
    $("reminderHint").textContent="Legg vakten til Mine vakter først.";
  }else if(!hasTime){
    $("reminderHint").textContent="KOVA må ha klokkeslett før push-påminnelse kan planlegges.";
  }else if(!state.favoriteOrgs.has(state.selected.orgCode||state.org)){
    $("reminderHint").textContent="Marker korpset som favoritt for å få pushvarsler. Kalenderpåminnelsen lagres uansett.";
  }else if(pushSupported()&&Notification.permission==="granted"&&localStorage.getItem("kova.pwa.pushRegisteredAt")){
    $("reminderHint").textContent="Push-påminnelse er koblet til Bridge og tas også med i kalenderfilen.";
  }else{
    $("reminderHint").textContent="Valget lagres og tas med i kalender. Aktiver varsler for push-påminnelse.";
  }
}
async function fetchJson(url){
  const separator=url.includes("?")?"&":"?";
  const freshUrl=url+`${separator}ts=${Date.now()}`;
  const response=await fetch(freshUrl,{cache:"no-store",signal:AbortSignal.timeout(15000)});
  if(!response.ok)throw new Error("Kunne ikke hente oppdaterte KOVA-data");
  return response.json();
}
async function loadOrganizations(){
  const [orgResult,indexResult]=await Promise.allSettled([
    fetchJson(`${DATA_BASE}/organizations.json`),
    fetchJson(`${DATA_BASE}/index.json`)
  ]);
  if(orgResult.status!=="fulfilled")throw orgResult.reason;
  state.orgs=orgResult.value.organizations.filter(o=>o.category==="hjelpekorps");
  if(indexResult.status==="fulfilled"){
    const indexRows=indexResult.value.organizations||[];
    state.orgIndex=new Map(indexRows.map(row=>[row.code,row]));
    const activeCodes=new Set(indexRows.filter(row=>Number(row.eventCount)>0).map(row=>row.code));
    state.orgs=state.orgs.filter(o=>activeCodes.has(o.code) || state.followed.has(o.code) || state.favoriteOrgs.has(o.code) || o.code===state.org);
    state.bridgeApiVersion=indexResult.value.apiVersion||"1.x";
    state.bridgeCapabilities=indexResult.value.capabilities||{};
  }
  if(!state.orgs.some(o=>o.code===state.org))state.org="UllensakerRKH";
  renderOrgOptions($("orgSearchInput")?.value||"");
  orgSelect.value=state.org;
  updateFollowUi();
}
function snapshotFile(code){
  const indexed=state.orgIndex.get(code)?.file;
  if(typeof indexed==="string" && /^[A-Za-z0-9._-]+\.json$/.test(indexed))return indexed;
  return code.replace(/[^A-Za-z0-9._-]+/g,"_").replace(/^_+|_+$/g,"")+".json";
}
const pendingOrgLoads=new Map();
async function loadOneOrg(code){
  if(pendingOrgLoads.has(code))return pendingOrgLoads.get(code);
  const task=loadOrgSnapshot(code);
  pendingOrgLoads.set(code,task);
  try{return await task}finally{pendingOrgLoads.delete(code)}
}
async function loadOrgSnapshot(code){
  const payload=await fetchJson(`${DATA_BASE}/${snapshotFile(code)}`);
  const events=(payload.events||[]).map(event=>({
    ...event,
    orgCode:code,
    orgName:payload.organization?.name||orgName(code),
  }));
  reconcileFavorites(code,events);
  return {payload,events};
}
async function loadCurrent(){
  const {payload,events}=await loadOneOrg(state.org);
  state.events=events;
  $("orgTitle").textContent=payload.organization?.name||state.org;
  statusText.textContent="KOVA-data oppdatert "+formatUpdated(payload.updatedAt);
}
async function loadWithConcurrency(codes,limit=6){
  const results=new Array(codes.length);
  let cursor=0;
  async function worker(){
    while(cursor<codes.length){
      const index=cursor++;
      try{
        results[index]={status:"fulfilled",value:await loadOneOrg(codes[index])};
      }catch(reason){
        results[index]={status:"rejected",reason};
      }
    }
  }
  await Promise.all(Array.from({length:Math.min(limit,codes.length)},()=>worker()));
  return results;
}
async function loadMany(codes,title,emptyText){
  if(!codes.length){
    state.events=[];
    $("orgTitle").textContent=title;
    statusText.textContent=emptyText;
    return;
  }
  const results=await loadWithConcurrency(codes,6);
  state.events=results.filter(r=>r.status==="fulfilled").flatMap(r=>r.value.events);
  $("orgTitle").textContent=title;
  const ok=results.filter(r=>r.status==="fulfilled").length;
  statusText.textContent=`Lastet ${ok} av ${codes.length} korps`;
}
async function loadFollowed(){
  await loadMany([...state.followed],"Fulgte korps","Ingen korps er fulgt ennå");
}
async function loadFavorites(){
  const codes=[...new Set([...state.favorites].map(key=>key.split("|")[0]).filter(Boolean))];
  await loadMany(codes,"Mine vakter","Ingen favorittvakter er lagret ennå");
}
async function warmOfflineCache(){
  if(!navigator.onLine)return;
  const favoriteCodes=[...state.favorites]
    .map(key=>key.split("|")[0])
    .filter(Boolean);
  const codes=[...new Set([state.org,...state.followed,...favoriteCodes])];
  await loadWithConcurrency(codes,4);
  localStorage.setItem("kova.pwa.offlineWarmedAt",new Date().toISOString());
}

async function loadEvents(){
  statusText.textContent="Oppdaterer…";
  updateConnection();
  try{
    if(state.view==="followed")await loadFollowed();
    else if(state.view==="favorites")await loadFavorites();
    else await loadCurrent();
    updateTypes();
    render();
  }catch(error){
    statusText.textContent=(navigator.onLine?"Kunne ikke hente KOVA":"Frakoblet")+" – "+(error.message||"ukjent feil");
    render();
  }
}
function formatUpdated(value){
  if(!value)return "";
  try{return new Intl.DateTimeFormat("nb-NO",{dateStyle:"short",timeStyle:"short"}).format(new Date(value))}
  catch{return value}
}
function escapeHtml(value=""){
  return String(value).replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#039;"}[c]));
}
function eventTime(event){
  return (event.time||"").match(/\d{1,2}:\d{2}/)?.[0]||"";
}
function calendarDate(event,time){
  const date=new Date(event.dateIso+"T00:00:00");
  if(time){
    const [h,m]=time.split(":").map(Number);
    date.setHours(h,m,0,0);
  }
  return date;
}
function icsDay(date){
  const p=n=>String(n).padStart(2,"0");
  return `${date.getFullYear()}${p(date.getMonth()+1)}${p(date.getDate())}`;
}
function icsStamp(date){
  const p=n=>String(n).padStart(2,"0");
  return `${icsDay(date)}T${p(date.getHours())}${p(date.getMinutes())}00`;
}
function escapeIcs(value=""){return String(value).replace(/\\/g,"\\\\").replace(/\n/g,"\\n").replace(/,/g,"\\,").replace(/;/g,"\\;")}
async function addToCalendar(event){
  const time=eventTime(event);
  const start=calendarDate(event,time);
  const end=new Date(start);
  if(time)end.setHours(end.getHours()+1); else end.setDate(end.getDate()+1);
  const dateLines=time
    ? [`DTSTART:${icsStamp(start)}`,`DTEND:${icsStamp(end)}`]
    : [`DTSTART;VALUE=DATE:${icsDay(start)}`,`DTEND;VALUE=DATE:${icsDay(end)}`];
  const reminderMinutes=reminderMinutesFor(event);
  const alarmLines=time&&reminderMinutes>0
    ? [
        "BEGIN:VALARM",
        `TRIGGER:-PT${reminderMinutes}M`,
        "ACTION:DISPLAY",
        `DESCRIPTION:${escapeIcs("Påminnelse: "+(event.description||"KOVA-aktivitet"))}`,
        "END:VALARM"
      ]
    : [];
  const ics=[
    "BEGIN:VCALENDAR","VERSION:2.0","PRODID:-//Kova Companion//PWA//NO","BEGIN:VEVENT",
    `UID:${escapeIcs(eventKey(event))}@kova-companion`,
    ...dateLines,
    `SUMMARY:${escapeIcs(event.description||"KOVA-aktivitet")}`,
    `DESCRIPTION:${escapeIcs((event.type||"Aktivitet")+" – "+(event.orgName||orgName(event.orgCode)))}`,
    `URL:${escapeIcs(event.sourceUrl||"https://www.kova.no/")}`,
    ...alarmLines,
    "END:VEVENT","END:VCALENDAR",""
  ].join("\r\n");
  const file=new File([ics],"kova-aktivitet.ics",{type:"text/calendar"});
  if(navigator.canShare?.({files:[file]})){
    await navigator.share({files:[file],title:event.description||"KOVA-aktivitet"});
    return;
  }
  const url=URL.createObjectURL(file);
  const a=document.createElement("a"); a.href=url; a.download=file.name; a.click();
  setTimeout(()=>URL.revokeObjectURL(url),1000);
}
async function shareEvent(event){
  const text=`${event.description||"KOVA-aktivitet"}\n${event.dateLabel||event.dateIso} ${displayTime(event.time)}\n${event.orgName||orgName(event.orgCode)}`;
  const url=event.sourceUrl||"https://www.kova.no/";
  if(navigator.share){
    await navigator.share({title:event.description||"KOVA-aktivitet",text,url});
  }else{
    await navigator.clipboard.writeText(text+"\n"+url);
    $("shareBtn").textContent="Kopiert";
    setTimeout(()=>{$("shareBtn").textContent="Del aktivitet"},1200);
  }
}

async function syncNotificationPreferenceControls(){
  state.notificationKinds=new Set(
    [
      ["added",$("notifyAdded").checked],
      ["changed",$("notifyChanged").checked],
      ["removed",$("notifyRemoved").checked]
    ].filter(([,enabled])=>enabled).map(([kind])=>kind)
  );
  state.quietEnabled=$("quietEnabled").checked;
  state.quietStartHour=timeHour($("quietStart").value,22);
  state.quietEndHour=timeHour($("quietEnd").value,7);
  saveNotificationPrefs();
  await syncPushOrganizations();
  await refreshNotificationUi();
}
function renderNotificationHistory(items=[]){
  const container=$("notificationHistory");
  container.innerHTML="";
  container.classList.toggle("hidden",!items.length);
  if(!items.length){
    container.classList.remove("hidden");
    container.textContent="Ingen lokale pushvarsler er lagret ennå.";
    return;
  }
  for(const item of items.slice(0,50)){
    const row=document.createElement("div");
    row.className="history-item";
    const title=document.createElement("strong");
    title.textContent=item.title||"KOVA-varsel";
    const body=document.createElement("span");
    body.textContent=item.body||"";
    const meta=document.createElement("span");
    meta.textContent=[
      item.timestamp?formatUpdated(item.timestamp):"",
      item.organization?orgName(item.organization):"",
      item.kind||""
    ].filter(Boolean).join(" • ");
    row.append(title,body,meta);
    container.appendChild(row);
  }
}
function requestNotificationHistory(){
  const controller=navigator.serviceWorker?.controller;
  if(!controller){
    renderNotificationHistory([]);
    return;
  }
  controller.postMessage({type:"get-notification-history"});
}
async function openNotificationTarget(data={}){
  const organization=data.organization||state.org;
  if(organization && state.orgs.some(org=>org.code===organization)){
    state.org=organization;
    localStorage.setItem("kova.pwa.org",state.org);
    renderOrgOptions($("orgSearchInput").value);
    orgSelect.value=state.org;
    state.view="all";
    document.querySelectorAll("#viewTabs button").forEach(button=>button.classList.toggle("active",button.dataset.view==="all"));
    await loadEvents();
  }
  const found=state.events.find(event=>event.id===data.eventId);
  if(found){
    openDetail({...found,changeSummary:data.changeSummary||""});
    return;
  }
  if(data.eventId && data.description){
    openDetail({
      id:data.eventId,
      orgCode:organization,
      orgName:orgName(organization),
      dateIso:data.dateIso||"",
      dateLabel:data.dateLabel||data.dateIso||"",
      time:data.time||"",
      type:data.eventType||"Aktivitet",
      description:data.description,
      sourceUrl:data.sourceUrl||"https://www.kova.no/",
      changeSummary:data.changeSummary||""
    });
  }
}

async function loadChangelog(){
  try{
    const url="https://firestore.googleapis.com/v1/projects/kova-companion/databases/(default)/documents/publicConfig/changelog";
    const response=await fetch(url,{cache:"no-store"});
    if(!response.ok)throw new Error("Ingen publisert endringslogg");
    const doc=await response.json(); const fields=doc.fields||{};
    $("changelogTitle").textContent=fields.title?.stringValue||"Kova Companion";
    $("changelogBody").textContent=fields.body?.stringValue||"Ingen endringer publisert ennå.";
    $("changelogDate").textContent=fields.updatedAt?.timestampValue?("Publisert "+formatUpdated(fields.updatedAt.timestampValue)):"";
  }catch(error){
    $("changelogTitle").textContent="Endringslogg";
    $("changelogBody").textContent="Ingen publisert endringslogg tilgjengelig ennå.";
    $("changelogDate").textContent="";
  }
}

async function queuedRefresh(){
  if(!navigator.onLine){
    localStorage.setItem("kova.pwa.pendingRefresh","1");
    statusText.textContent="Frakoblet – oppdaterer automatisk når nettet er tilbake";
    return;
  }
  localStorage.removeItem("kova.pwa.pendingRefresh");
  await loadEvents();
}

orgSelect.addEventListener("change",async()=>{
  state.org=orgSelect.value;
  localStorage.setItem("kova.pwa.org",state.org);
  updateFollowUi();
  if(state.view==="followed"){
    state.view="all";
    document.querySelectorAll("#viewTabs button").forEach(b=>b.classList.toggle("active",b.dataset.view==="all"));
  }
  await loadEvents();
});
$("orgSearchInput").addEventListener("input",()=>{
  renderOrgOptions($("orgSearchInput").value);
});
$("favoriteOrgBtn").onclick=async()=>{
  state.favoriteOrgs.has(state.org)?state.favoriteOrgs.delete(state.org):state.favoriteOrgs.add(state.org);
  saveSet("kova.pwa.favoriteOrgs",state.favoriteOrgs);
  renderOrgOptions($("orgSearchInput").value);
  orgSelect.value=state.org;
  updateFavoriteOrgUi();
  await syncPushOrganizations();
  await refreshNotificationUi();
};
for(const id of ["notifyAdded","notifyChanged","notifyRemoved","quietEnabled","quietStart","quietEnd"]){
  $(id).addEventListener("change",()=>syncNotificationPreferenceControls().catch(console.warn));
}
$("notificationHistoryBtn").onclick=()=>{
  $("notificationHistory").classList.toggle("hidden");
  requestNotificationHistory();
};
document.querySelectorAll(".quick-nav button[data-scroll]").forEach(button=>{
  button.onclick=()=>$(button.dataset.scroll)?.scrollIntoView({behavior:"smooth",block:"start"});
});
document.querySelectorAll(".quick-nav button[data-view-jump]").forEach(button=>{
  button.onclick=async()=>{
    state.view=button.dataset.viewJump;
    state.displayLimit=20;
    document.querySelectorAll("#viewTabs button").forEach(tab=>tab.classList.toggle("active",tab.dataset.view===state.view));
    await loadEvents();
    $("orgTitle").scrollIntoView({behavior:"smooth",block:"start"});
  };
});
typeSelect.addEventListener("change",()=>{state.type=typeSelect.value;state.displayLimit=20;render()});
searchInput.addEventListener("input",()=>{state.search=searchInput.value;state.displayLimit=20;render()});
$("refreshBtn").onclick=queuedRefresh;
$("followBtn").onclick=async()=>{
  state.followed.has(state.org)?state.followed.delete(state.org):state.followed.add(state.org);
  saveSet("kova.pwa.followed",state.followed);
  updateFollowUi();
  await syncPushOrganizations();
  await refreshNotificationUi();
  if(state.view==="followed")await loadEvents();
};
$("viewTabs").addEventListener("click",async event=>{
  const btn=event.target.closest("button[data-view]"); if(!btn)return;
  state.view=btn.dataset.view;
  state.displayLimit=20;
  document.querySelectorAll("#viewTabs button").forEach(b=>b.classList.toggle("active",b===btn));
  await loadEvents();
});
$("closeDialog").onclick=()=> $("detailDialog").close();
$("favoriteDialogBtn").onclick=async()=>{
  if(!state.selected)return;
  await toggleFavorite(state.selected);
  updateReminderUi();
};
$("detailNote").addEventListener("input",()=>{
  if(state.selected)saveNote(state.selected,$("detailNote").value);
});
$("reminderSelect").addEventListener("change",async()=>{
  if(!state.selected)return;
  const minutes=Number($("reminderSelect").value||0);
  $("reminderSelect").disabled=true;
  updateReminderUi("Lagrer påminnelse…");
  try{
    const registered=await saveReminder(state.selected,minutes);
    updateReminderUi(
      minutes===0
        ? "Påminnelsen er slått av."
        : registered
          ? `Påminnelse lagret: ${reminderLabel(minutes)} før vakten.`
          : `Påminnelse lagret lokalt: ${reminderLabel(minutes)} før. Aktiver varsler for push.`
    );
  }catch(error){
    updateReminderUi("Kunne ikke lagre push-påminnelsen: "+(error.message||String(error)));
  }finally{
    updateDialogFavorite();
  }
});
$("loadMoreBtn").onclick=()=>{
  state.displayLimit+=20;
  render();
};
$("myShiftsAllBtn").onclick=async()=>{
  state.view="favorites";
  state.displayLimit=20;
  document.querySelectorAll("#viewTabs button").forEach(button=>button.classList.toggle("active",button.dataset.view==="favorites"));
  await loadEvents();
  $("orgTitle").scrollIntoView({behavior:"smooth",block:"start"});
};
$("calendarBtn").onclick=async()=>{if(state.selected)await addToCalendar(state.selected)};
$("shareBtn").onclick=async()=>{if(state.selected)await shareEvent(state.selected)};
$("notificationBtn").onclick=toggleNotifications;

let lastForegroundRefresh=0;
let foregroundRefresh=null;
function refreshOnForeground(){
  if(foregroundRefresh)return foregroundRefresh;
  foregroundRefresh=runForegroundRefresh().catch(console.warn).finally(()=>{foregroundRefresh=null});
  return foregroundRefresh;
}
async function runForegroundRefresh(){
  const reloading=await checkRemoteCacheEpoch();
  if(reloading)return;
  const now=Date.now();
  if(now-lastForegroundRefresh>60000){
    lastForegroundRefresh=now;
    await loadEvents();
  }
  await repairPushOnResume();
}
window.addEventListener("online",()=>{
  updateConnection();
  if(localStorage.getItem("kova.pwa.pendingRefresh")==="1"){
    queuedRefresh().catch(()=>{});
  }else{
    refreshOnForeground();
  }
});
window.addEventListener("offline",updateConnection);
window.addEventListener("focus",refreshOnForeground);
document.addEventListener("visibilitychange",()=>{
  if(document.visibilityState==="visible") refreshOnForeground();
});
setInterval(()=>{
  if(document.visibilityState==="visible" && navigator.onLine) loadEvents();
},5*60*1000);

let installPrompt=null;
window.addEventListener("beforeinstallprompt",event=>{
  event.preventDefault(); installPrompt=event; $("installBtn").classList.remove("hidden");
});
$("installBtn").onclick=async()=>{
  if(!installPrompt)return;
  await installPrompt.prompt(); installPrompt=null; $("installBtn").classList.add("hidden");
};
if(isIOS()&&!isStandalone())$("installHint").classList.remove("hidden");

if("serviceWorker" in navigator){
  navigator.serviceWorker.addEventListener("message",event=>{
    if(event.data?.type==="pwa-updated") repairPushOnResume();
    if(event.data?.type==="notificationclick") openNotificationTarget(event.data).catch(console.warn);
    if(event.data?.type==="notification-history-response") renderNotificationHistory(event.data.items||[]);
  });
  let refreshingWorker=false;
  navigator.serviceWorker.register("./sw.js",{updateViaCache:"none"}).then(async registration=>{
    try{await registration.update()}catch{}
    navigator.serviceWorker.addEventListener("controllerchange",()=>{
      if(refreshingWorker)return;
      refreshingWorker=true;
      location.reload();
    });
  }).catch(()=>{});
}

(async()=>{
  updateConnection();
  try{
    await loadOrganizations();
    await loadChangelog();
    updateNotificationSettingsUi();
    const reloading=await checkRemoteCacheEpoch();
    if(reloading)return;
    await loadEvents();
    await refreshFavoriteDashboard();
    warmOfflineCache().catch(()=>{});
    await repairPushRegistration();
    await refreshNotificationUi();
    const params=new URLSearchParams(location.search);
    if(params.get("event")){
      await openNotificationTarget({
        organization:params.get("org")||state.org,
        eventId:params.get("event")||""
      });
    }
  }catch(error){
    statusText.textContent=error.message||"Oppstart feilet";
  }
})();
