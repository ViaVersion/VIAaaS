// https://stackoverflow.com/questions/42127148/service-worker-communicate-to-clients
let viac = new BroadcastChannel("viaaas-notification");
self.addEventListener("notificationclick", event => {
    event.preventDefault();
    event.notification.close();
    viac.postMessage({tag: event.notification.tag, action: event.action});
});

// stolen from https://github.com/mozilla/serviceworker-cookbook/blob/master/strategy-network-or-cache/service-worker.js (MIT license)

let cacheId = "viaaas";

self.addEventListener("install", () => {
});

self.addEventListener("fetch", evt => {
    let req = evt.request;
    if (!isImmutable(req.url) || req.method !== "GET") return;
    evt.respondWith(fromCache(req)
        .catch(err => {
            return saveToCache(err, req)
                .then(() => fetch(req))
        })
    );
});

function isImmutable(url) {
    let parsed = new URL(url);
    return ["cdnjs.cloudflare.com", "alcdn.msauth.net"].indexOf(parsed.host) !== -1;
}

async function saveToCache(err, request) {
    if (!isImmutable(request.url)) return
    console.log("caching due to: " + err, request);
    let cache = await caches.open(cacheId);
    // passing request directly doesn't work well with workers due to opaque response
    await cache.add(request.url);
}

function fromCache(request) {
    return caches.open(cacheId)
        .then(cache => cache.match(request))
        .then(matched => {
            if (matched == null) return Promise.reject("no match");
            return matched;
        });
}
