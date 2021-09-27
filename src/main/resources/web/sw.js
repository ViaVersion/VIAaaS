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
    if (!shouldCache(evt.request.url)
        || evt.request.method !== "GET") return;
    evt.respondWith(
        fromCache(evt.request).catch(() => fromNetwork(evt.request))
    );
});

addEventListener("message", e => {
    if (e.data.action === "cache") {
        e.waitUntil(cache(e.data.urls));
    }
});

function shouldCache(it) {
    return [".js", ".css", ".png", ".html", ".webp", "manifest.json"].findIndex(end => it.endsWith(end)) !== -1
        || it === new URL("./", self.location).toString()
}

function cache(urls) {
    let filtered = Array.from(new Set(urls.filter(shouldCache)));
    return caches.open(cacheId).then(cache => cache.addAll(filtered));
}

function fromNetwork(request) {
    return fetch(request)
        .then(response => {
            if (!shouldCache(response.url)) return response;

            // Let's cache it when loading for the first time
            return caches.open(cacheId)
                .then(it => it.add(request))
                .then(() => response);
        });
}

function fromCache(request) {
    return caches.open(cacheId)
        .then(cache => cache.match(request))
        .then(matching => matching || Promise.reject("no-match"));
}
