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
    if (!isImmutable(evt.request.url) || evt.request.method !== "GET") return;
    evt.respondWith(fromCache(evt.request).catch(() => tryNetwork(evt.request)));
});

function isImmutable(url) {
    let parsed = new URL(url);
    return ["cdnjs.cloudflare.com", "alcdn.msauth.net"].indexOf(parsed.host) !== -1;
}

function tryNetwork(request) {
    return fetch(request)
        .then(async response => {
            if (!isImmutable(request.url)) return response;

            try {
                await fromCache(request)
                    .catch(async e => {
                        console.log("caching due to: " + e);
                        console.log(request);
                        let cache = await caches.open(cacheId);
                        // passing request directly doesn't work well with workers due to opaque response
                        await cache.add(request.url);
                    });
            } catch (e) {
                console.log("failed to cache: " + e)
            }
            return response;
        });
}

function fromCache(request) {
    return caches.open(cacheId)
        .then(async cache => {
            let matching = await cache.match(request);
            if (matching == null) return Promise.reject("no match");

            return matching;
        });
}
