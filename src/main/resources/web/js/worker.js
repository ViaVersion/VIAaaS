"use strict";
importScripts("https://cdnjs.cloudflare.com/ajax/libs/js-sha512/0.8.0/sha512.min.js");

let pending = [];

self.addEventListener("message", e => {
    if (e.data.action === "listen_pow") startPoW(e);
    if (e.data.action === "cancel") removePending(e.data.id);
});

function startPoW(e) {
    pending.push(e.data.id);
    setTimeout(() => listenPoW(e.data));
}

function removePending(id) {
    pending = pending.filter(it => it !== id);
}

function isPending(id) {
    return pending.includes(id);
}

function listenPoW(data) {
    if (!isPending(data.id)) return; // cancelled
    let candidateMsg = "";
    let chunkEndTime = Date.now() + 500;
    do {
        if (Date.now() >= chunkEndTime) {
            setTimeout(() => listenPoW(data));
            return;
        }

        let correctedDate = Date.now() - data.deltaTime
        candidateMsg = JSON.stringify({
            action: "offline_login",
            username: data.user,
            challenge: data.challenge,
            date: correctedDate,
            rand: Math.random()
        });
    } while (!sha512(candidateMsg).startsWith("00000"));

    setTimeout(() => {
        if (!isPending(data.id)) return;
        postMessage({
            id: data.id,
            action: "completed_pow",
            msg: candidateMsg
        });
    })
}