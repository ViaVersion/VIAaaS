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
  evt.waitUntil(cache(["./"]));
});

self.addEventListener("fetch", evt => {
  if (event.request.mode != "navigate") return;
  evt.respondWith(
    fromNetwork(evt.request)
      .catch(() => fromCache(evt.request))
  );
});

addEventListener("message", e => {
  if (e.data.action == "cache") {
    e.waitUntil(cache(e.data.urls));
  }
});

function cache(urls) {
  return caches.open(CACHE).then(cache => cache.addAll(urls));
}

function fromNetwork(request) {
  return fetch(request);
}

function fromCache(request) {
  return caches.open(CACHE)
    .then(cache => cache.match(request))
    .then(matching => matching || Promise.reject("no-match"));
}
