importScripts("https://cdnjs.cloudflare.com/ajax/libs/js-sha512/0.8.0/sha512.min.js");

let pending = [];

onmessage = function (e) {
    if (e.data.action === "listen_pow") startPoW(e);
    if (e.data.action === "cancel") removePending(e.data.id);
}

function removePending(id) {
    pending = pending.filter(it => it !== id);
}

function startPoW(e) {
    pending.push(e.data.id);
    listenPoW(e);
}

function listenPoW(e) {
    let user = e.data.user;
    let msg = null;
    let endTime = Date.now() + 1000;
    do {
        if (!pending.includes(e.data.id)) return; // cancelled

        msg = JSON.stringify({
            action: "offline_login",
            username: user,
            date: Date.now() - e.data.deltaTime,
            rand: Math.random()
        });

        if (Date.now() >= endTime) {
            setTimeout(() => listenPoW(e), 0);
            return;
        }
    } while (!sha512(msg).startsWith("00000"));

    postMessage({id: e.data.id, action: "completed_pow", msg: msg});
}

/* function sha512(s) {
    const shaObj = new jsSHA("SHA-512", "TEXT", { encoding: "UTF8" });
    shaObj.update(s);
    return shaObj.getHash("HEX");
} */
