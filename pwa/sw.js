const CACHE="kova-companion-pwa-v33";
const SHELL=["./","./index.html","./styles.css","./app.js","./manifest.webmanifest","./icon.svg","./firebase-web-config.json","./privacy.html"];
const HISTORY_DB="kova-companion-pwa-history";
const HISTORY_STORE="notifications";

function historyDb(){
  return new Promise((resolve,reject)=>{
    const request=indexedDB.open(HISTORY_DB,1);
    request.onupgradeneeded=()=>{
      const db=request.result;
      if(!db.objectStoreNames.contains(HISTORY_STORE)){
        const store=db.createObjectStore(HISTORY_STORE,{keyPath:"key"});
        store.createIndex("timestamp","timestamp");
      }
    };
    request.onsuccess=()=>resolve(request.result);
    request.onerror=()=>reject(request.error);
  });
}

async function storeNotification(item){
  const db=await historyDb();
  const duplicate=await new Promise((resolve,reject)=>{
    const tx=db.transaction(HISTORY_STORE,"readwrite");
    const store=tx.objectStore(HISTORY_STORE);
    const get=store.get(item.key);
    get.onsuccess=()=>{
      if(get.result){
        resolve(true);
        return;
      }
      store.put(item);
      resolve(false);
    };
    get.onerror=()=>reject(get.error);
  });
  if(duplicate)return true;

  const items=await readHistory();
  if(items.length>100){
    const remove=items.slice(100);
    await new Promise((resolve,reject)=>{
      const tx=db.transaction(HISTORY_STORE,"readwrite");
      const store=tx.objectStore(HISTORY_STORE);
      remove.forEach(row=>store.delete(row.key));
      tx.oncomplete=resolve;
      tx.onerror=()=>reject(tx.error);
    });
  }
  return false;
}

async function readHistory(){
  const db=await historyDb();
  return new Promise((resolve,reject)=>{
    const tx=db.transaction(HISTORY_STORE,"readonly");
    const request=tx.objectStore(HISTORY_STORE).getAll();
    request.onsuccess=()=>resolve((request.result||[]).sort((a,b)=>b.timestamp-a.timestamp));
    request.onerror=()=>reject(request.error);
  });
}

self.addEventListener("install",event=>{
  event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(
    SHELL.map(path=>new Request(new URL(path,self.location.href),{cache:"reload"}))
  )));
  self.skipWaiting();
});

self.addEventListener("activate",event=>{
  event.waitUntil((async()=>{
    const keys=await caches.keys();
    await Promise.all(keys.filter(key=>key.startsWith("kova-companion-pwa-")&&key!==CACHE).map(key=>caches.delete(key)));
    await self.clients.claim();
    const windows=await self.clients.matchAll({type:"window",includeUncontrolled:true});
    for(const client of windows)client.postMessage({type:"pwa-updated"});
  })());
});

self.addEventListener("fetch",event=>{
  if(event.request.method!=="GET")return;
  const url=new URL(event.request.url);

  const isAdmin=url.origin===self.location.origin && url.pathname.startsWith(new URL("./admin/", self.location.href).pathname);
  if(event.request.mode==="navigate"){
    // Admin navigation must never replace the offline user-app shell.
    const navigationKey=isAdmin?event.request:"./index.html";
    event.respondWith(
      fetch(event.request).then(async response=>{
        if(response.ok){
          const cache=await caches.open(CACHE);
          await cache.put(navigationKey,response.clone());
        }
        return response;
      }).catch(async()=>
        (await caches.match(navigationKey)) ||
        (!isAdmin && await caches.match("./")) ||
        new Response("Du er frakoblet. Åpne siden igjen når du har nett.",{status:503})
      )
    );
    return;
  }

  if(isAdmin){
    event.respondWith(
      fetch(event.request).then(async response=>{
        if(response.ok){
          const cache=await caches.open(CACHE);
          await cache.put(event.request,response.clone());
        }
        return response;
      }).catch(()=>caches.match(event.request))
    );
    return;
  }

  const isData=url.hostname==="raw.githubusercontent.com" && url.pathname.includes("/bridge/data/");
  if(isData){
    const cacheUrl=new URL(event.request.url);
    cacheUrl.searchParams.delete("ts");
    const cacheKey=new Request(cacheUrl.toString(),{
      method:"GET",
      headers:event.request.headers,
      mode:event.request.mode,
      credentials:event.request.credentials,
      redirect:event.request.redirect
    });
    event.respondWith(
      fetch(event.request).then(response=>{
        const copy=response.clone();
        caches.open(CACHE).then(cache=>cache.put(cacheKey,copy));
        return response;
      }).catch(()=>caches.match(cacheKey))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then(cached=>{
      const network=fetch(event.request).then(response=>{
        if(url.origin===self.location.origin){
          const copy=response.clone();
          caches.open(CACHE).then(cache=>cache.put(event.request,copy));
        }
        return response;
      }).catch(()=>cached);
      return cached||network;
    })
  );
});

self.addEventListener("notificationclick",event=>{
  event.notification.close();
  const data=event.notification.data||{};
  const params=new URLSearchParams();
  if(data.organization)params.set("org",data.organization);
  if(data.eventId)params.set("event",data.eventId);
  const appUrl=new URL("./",self.location.href);
  let target=new URL(data.url||("./"+(params.toString()?"?"+params.toString():"")),appUrl);
  if(target.origin!==appUrl.origin || !target.pathname.startsWith(appUrl.pathname))target=appUrl;
  event.waitUntil((async()=>{
    const list=await self.clients.matchAll({type:"window",includeUncontrolled:true});
    const existing=list.find(client=>new URL(client.url).pathname===appUrl.pathname && "focus" in client);
    if(existing){
      const navigated=await existing.navigate(target.href);
      return (navigated||existing).focus();
    }
    return self.clients.openWindow(target.href);
  })());
});

self.addEventListener("push",event=>{
  event.waitUntil((async()=>{
    let payload={};
    try{payload=event.data?.json()||{}}catch{payload={body:event.data?.text()||""}}
    const eventData=payload.event||{};
    const title=payload.title||"Kova Companion";
    const body=payload.body||"Ny KOVA-oppdatering";
    const key=payload.changeId||[
      payload.kind||"unknown",
      payload.organization||"",
      eventData.id||"",
      Date.now()
    ].join("|");

    const historyItem={
      key,
      timestamp:Date.now(),
      title,
      body,
      kind:payload.kind||"",
      organization:payload.organization||"",
      eventId:eventData.id||"",
      dateIso:eventData.dateIso||"",
      dateLabel:eventData.dateLabel||"",
      time:eventData.time||"",
      eventType:eventData.type||"",
      description:eventData.description||"",
      sourceUrl:eventData.sourceUrl||"",
      changeSummary:payload.changeSummary||""
    };

    const options={
      body,
      icon:"./icon.svg",
      badge:"./icon.svg",
      tag:key,
      renotify:false,
      data:{
        url:payload.url||"",
        organization:historyItem.organization,
        eventId:historyItem.eventId,
        kind:historyItem.kind,
        dateIso:historyItem.dateIso,
        dateLabel:historyItem.dateLabel,
        time:historyItem.time,
        eventType:historyItem.eventType,
        description:historyItem.description,
        sourceUrl:historyItem.sourceUrl,
        changeSummary:historyItem.changeSummary
      }
    };
    // Every iOS push must display a notification. Use the tag to replace duplicates;
    // never let IndexedDB errors or history deduplication suppress visible delivery.
    await self.registration.showNotification(title,options);
    try{await storeNotification(historyItem)}catch(error){
      console.warn("Kunne ikke lagre varselhistorikk",error);
    }
  })());
});

self.addEventListener("message",event=>{
  if(event.data?.type==="get-notification-history"){
    event.waitUntil(readHistory().then(items=>{
      if(event.source?.postMessage){
        event.source.postMessage({type:"notification-history-response",items});
      }
    }));
  }
});

self.addEventListener("pushsubscriptionchange",event=>{
  event.waitUntil(
    self.clients.matchAll({type:"window",includeUncontrolled:true}).then(list=>{
      for(const client of list)client.postMessage({type:"pushsubscriptionchange"});
    })
  );
});
