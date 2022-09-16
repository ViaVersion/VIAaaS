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
    if (!shouldCache(evt.request.url) || evt.request.method !== "GET") return;
    if (isImmutable(evt.request.url)) {
        evt.respondWith(fromCache(evt.request).catch(() => tryNetworkAndCache(evt.request)));
    } else {
        evt.respondWith(tryNetworkAndCache(evt.request).catch(() => fromCache(evt.request)));
    }
});

function isImmutable(url) {
    let parsed = new URL(url);
    return ["cdnjs.cloudflare.com", "alcdn.msauth.net"].indexOf(parsed.host) !== -1;
}

function shouldCache(it) {
    return [".js", ".css", ".png", ".html", ".webp", "manifest.json"].findIndex(end => it.endsWith(end)) !== -1
        || it === new URL("./", self.location).toString()
}

function tryNetworkAndCache(request) {
    return fetch(request)
        .then(async response => {
            if (!shouldCache(response.url)) return response;

            try {
                await fromCache(request);
            } catch (e) {
                console.log("caching due to: " + e);
                let cache = await caches.open(cacheId);
                await cache.add(request);
            }
            return response;
        });
}

function fromCache(request) {
    return caches.open(cacheId)
        .then(async cache => {
            let matching = await cache.match(request);
            if (matching == null) return Promise.reject("no match");

            let timeDiff = new Date() - new Date(matching.headers.get("date"));
            if (!isImmutable(request.url) && timeDiff >= 24 * 60 * 60 * 1000) {
                await cache.delete(request);
                return Promise.reject("expired");
            }
            return matching;
        });
}
