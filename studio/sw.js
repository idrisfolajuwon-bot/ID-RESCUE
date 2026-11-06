const CACHE="idris-ai-studio-v14";
const CORE=["./","./index.html","./manifest.webmanifest","./icon.svg"];
self.addEventListener("install",event=>{event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(CORE)));self.skipWaiting()});
self.addEventListener("activate",event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith("idris-ai-studio-")&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim()))});
self.addEventListener("fetch",event=>{
 const req=event.request,url=new URL(req.url);
 if(req.method!=="GET"||url.origin!==self.location.origin)return;
 if(url.pathname.includes("/chat")||url.hostname.includes("workers.dev"))return;
 if(req.mode==="navigate"||url.pathname.endsWith("/index.html")){
  event.respondWith(fetch(req,{cache:"no-store"}).then(res=>{if(res.ok){const copy=res.clone();caches.open(CACHE).then(cache=>cache.put(req,copy))}return res}).catch(()=>caches.match(req).then(cached=>cached||caches.match("./index.html"))));
  return;
 }
 event.respondWith(caches.match(req).then(cached=>cached||fetch(req).then(res=>{if(res.ok){const copy=res.clone();caches.open(CACHE).then(cache=>cache.put(req,copy))}return res}).catch(()=>caches.match("./index.html"))));
});