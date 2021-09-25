// https://stackoverflow.com/questions/42127148/service-worker-communicate-to-clients
let viac = new BroadcastChannel("viaaas-notification");
self.addEventListener("notificationclick", event => {
  event.preventDefault();
  event.notification.close();
  viac.postMessage({tag: event.notification.tag, action: event.action});
});

// stolen from https://github.com/mozilla/serviceworker-cookbook/blob/master/strategy-network-or-cache/service-worker.js (MIT license)

var CACHE = "network-or-cache";

self.addEventListener("install", evt => {
  evt.waitUntil(cache(["./index.html"]));
});

self.addEventListener("fetch", evt => {
  return; // todo fix
  if (!shouldCache(evt.request.url)
    || evt.request.method != "GET") return;
  evt.respondWith(
    fromCache(evt.request).catch(() => fromNetwork(evt.request))
  );
});

addEventListener("message", e => {
  if (e.data.action == "cache") {
    e.waitUntil(cache(e.data.urls));
  }
});

function shouldCache(it) {
  return it.endsWith(".js") || it.endsWith(".css") || it.endsWith(".png") || it.endsWith(".html")
}

function cache(urls) {
  return; // todo fix
  return caches.open(CACHE).then(cache => cache.addAll(urls.filter(shouldCache)));
}

function fromNetwork(request) {
  return fetch(request);
}

function fromCache(request) {
  return caches.open(CACHE)
    .then(cache => cache.match(request))
    .then(matching => matching || Promise.reject("no-match"));
}
