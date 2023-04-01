"use strict";
importScripts("https://cdnjs.cloudflare.com/ajax/libs/js-sha512/0.8.0/sha512.min.js");

let pending = [];

self.addEventListener("message", e => {
    if (e.data.action === "listen_pow") startPoW(e);
    if (e.data.action === "cancel") removePending(e.data.id);
});

function removePending(id) {
    pending = pending.filter(it => it !== id);
}

function startPoW(e) {
    pending.push(e.data.id);
    listenPoW(e);
}

function isPending(id) {
    return pending.includes(id);
}

function listenPoW(e) {
    let user = e.data.user;
    let msg = null;
    let endTime = Date.now() + 1000;
    do {
        if (!isPending(e.data.id)) return; // cancelled

        msg = JSON.stringify({
            action: "offline_login",
            username: user,
            date: Date.now() - e.data.deltaTime,
            rand: Math.random()
        });

        if (Date.now() >= endTime) {
            setTimeout(() => listenPoW(e));
            return;
        }
    } while (!sha512(msg).startsWith("00000"));

    setTimeout(() => {
        if (!isPending(e.data.id)) return;
        postMessage({id: e.data.id, action: "completed_pow", msg: msg});
    })
}