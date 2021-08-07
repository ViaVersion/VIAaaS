// Minecraft.id
import {icanhazepoch, icanhazip} from "./util.js";
import {getCorsProxy, setCorsProxy} from "./cors_proxy.js";
import {
    findAccountByMs,
    getActiveAccounts,
    getMicrosoftUsers,
    loginMc,
    loginMs, MicrosoftAccount,
    MojangAccount
} from "./account_manager.js";
import {connect, getWsUrl, removeToken, sendSocket, setWsUrl, unlisten} from "./websocket.js";

let mcIdUsername = null;
let mcauth_code = null;
let mcauth_success = null;

$(() => {
    let urlParams = new URLSearchParams();
    window.location.hash.substr(1).split("?")
        .map(it => new URLSearchParams(it)
            .forEach((a, b) => urlParams.append(b, a)));
    mcIdUsername = urlParams.get("username");
    mcauth_code = urlParams.get("mcauth_code");
    mcauth_success = urlParams.get("mcauth_success");
    if (mcauth_success === "false") {
        addToast("Couldn't authenticate with Minecraft.ID", urlParams.get("mcauth_msg"));
    }
    if (mcauth_code != null) {
        history.replaceState(null, null, "#");
        renderActions();
    }
});

let connectionStatus = document.getElementById("connection_status");
let corsStatus = document.getElementById("cors_status");
let listening = document.getElementById("listening");
let actions = document.getElementById("actions");
let accounts = document.getElementById("accounts-list");
let cors_proxy_txt = document.getElementById("cors-proxy");
let ws_url_txt = document.getElementById("ws-url");
let listenVisible = false;
let workers = [];
$(() => workers = new Array(navigator.hardwareConcurrency).fill(null).map(() => new Worker("js/worker.js")));
window.addEventListener('beforeinstallprompt', e => {
    e.preventDefault();
});

// On load
$(() => {
    if (navigator.serviceWorker) {
        navigator.serviceWorker.register("sw.js").then(() => {
            swCacheFiles();
        });
    }

    ohNo();
    cors_proxy_txt.value = getCorsProxy();
    ws_url_txt.value = getWsUrl();
    $("form").on("submit", e => e.preventDefault());
    $("#form_add_mc").on("submit", () => loginMc($("#email").val(), $("#password").val()));
    $("#form_add_ms").on("submit", () => loginMs());
    $("#form_ws_url").on("submit", () => setWsUrl($("#ws-url").val()));
    $("#form_cors_proxy").on("submit", () => setCorsProxy($("#cors-proxy").val()));
    $(".css_async").attr("disabled", null);

    workers.forEach(it => it.onmessage = onWorkerMsg);
    refreshAccountList();
    setInterval(refreshCorsStatus, 10 * 60 * 1000); // Heroku auto sleeps in 30 min
    refreshCorsStatus();
    resetHtml();

    connect();
});

function swCacheFiles() {
    navigator.serviceWorker.ready.then(ready => ready.active.postMessage({
        action: "cache",
        urls: performance.getEntriesByType("resource")
            .map(it => it.name)
            .filter(it => it.endsWith(".js") || it.endsWith(".css") || it.endsWith(".png"))
    })); // https://stackoverflow.com/questions/46830493/is-there-any-way-to-cache-all-files-of-defined-folder-path-in-service-worker
}

export function setWsStatus(txt) {
    connectionStatus.innerText = txt;
}

export function setListenVisible(visible) {
    listenVisible = visible;
}

export function refreshCorsStatus() {
    corsStatus.innerText = "...";
    icanhazip(true).then(ip => {
        return icanhazip(false).then(ip2 => corsStatus.innerText = "OK " + ip + (ip !== ip2 ? " (different IP)" : ""));
    }).catch(e => corsStatus.innerText = "error: " + e);
}

function addMcAccountToList(account) {
    let p = document.createElement("li");
    p.className = "input-group d-flex";
    let shead = document.createElement("span");
    shead.className = "input-group-text";
    let head = document.createElement("img");
    shead.append(head);
    let n = document.createElement("span");
    n.className = "form-control";
    let remove = document.createElement("a");
    remove.className = "btn btn-danger";
    n.innerText = " " + account.name + " " + (account instanceof MicrosoftAccount ? "(" + account.msUser + ") " : "");
    remove.innerText = "Logout";
    remove.href = "javascript:";
    remove.onclick = () => {
        account.logout();
    };
    head.width = 24;
    head.alt = account.name + "'s head";
    head.src = "https://crafthead.net/helm/" + account.id;
    //(id.length == 36 || id.length == 32) ? "https://crafatar.com/avatars/" + id + "?overlay" : "https://crafthead.net/helm/" + id;
    p.append(shead);
    p.append(n);
    p.append(remove);
    accounts.appendChild(p);
}

export function refreshAccountList() {
    accounts.innerHTML = "";
    getActiveAccounts()
        .filter(it => it instanceof MojangAccount)
        .sort((a, b) => a.name.localeCompare(b.name))
        .forEach(it => addMcAccountToList(it));
    getMicrosoftUsers()
        .sort((a, b) => a.localeCompare(b))
        .forEach(username => {
            let mcAcc = findAccountByMs(username);
            if (!mcAcc) return;
            addMcAccountToList(mcAcc);
        });
}

export function renderActions() {
    actions.innerHTML = "";
    if (Notification.permission === "default") {
        actions.innerHTML += '<p><a href="javascript:" id="notificate">Enable notifications</a></p>';
        $("#notificate").on("click", () => Notification.requestPermission().then(renderActions)); // i'm lazy
    }
    if (listenVisible) {
        if (mcIdUsername != null && mcauth_code != null) {
            addAction("Listen to " + mcIdUsername, () => {
                sendSocket(JSON.stringify({
                    "action": "minecraft_id_login",
                    "username": mcIdUsername,
                    "code": mcauth_code
                }));
                mcauth_code = null;
                renderActions();
            });
        }
        addAction("Listen to frontend premium login in VIAaaS instance", () => {
            let user = prompt("Premium username (case-sensitive): ", "");
            if (!user) return;
            let callbackUrl = new URL(location);
            callbackUrl.search = "";
            callbackUrl.hash = "#username=" + encodeURIComponent(user);
            location.href = "https://api.minecraft.id/gateway/start/" + encodeURIComponent(user)
                + "?callback=" + encodeURIComponent(callbackUrl);
        });
        addAction("Listen to frontend offline login in VIAaaS instance", () => {
            let user = prompt("Offline username (case-sensitive):", "");
            if (!user) return;
            let taskId = Math.random();
            workers.forEach(it => it.postMessage({action: "listen_pow", user: user, id: taskId}));
            addToast("Offline username", "Please wait a minute...");
        });
    }
}

function onWorkerMsg(e) {
    console.log(e);
    if (e.data.action === "completed_pow") onCompletedPoW(e);
}

function onCompletedPoW(e) {
    addToast("Offline username", "Completed proof of work");
    workers.forEach(it => it.postMessage({action: "cancel", id: e.data.id}));
    sendSocket(e.data.msg);
}

function addAction(text, onClick) {
    let p = document.createElement("p");
    let link = document.createElement("a");
    p.appendChild(link);
    link.innerText = text;
    link.href = "javascript:";
    link.onclick = onClick;
    actions.appendChild(p);
}

export function addListeningList(user, token) {
    let p = document.createElement("p");
    let head = document.createElement("img");
    let n = document.createElement("span");
    let remove = document.createElement("a");
    n.innerText = " " + user + " ";
    remove.innerText = "Unlisten";
    remove.href = "javascript:";
    remove.onclick = () => {
        removeToken(token);
        listening.removeChild(p);
        unlisten(user);
    };
    head.width = 24;
    head.alt = user + "'s head";
    head.src = "https://crafthead.net/helm/" + user;
    p.append(head);
    p.append(n);
    p.append(remove);
    listening.appendChild(p);
}

export function addToast(title, msg) {
    let toast = document.createElement("div");
    document.getElementById("toasts").prepend(toast);
    $(toast)
        .attr("class", "toast")
        .attr("role", "alert")
        .attr("aria-live", "assertive")
        .attr("aria-atomic", "true") // todo sanitize date \/
        .html(`
<div class="toast-header">
  <strong class="me-auto toast_title_msg"></strong>
  <small class="text-muted">${new Date().toLocaleString()}</small>
  <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
</div>
<div class="toast-body"></div>
        `);
    $(toast).find(".toast_title_msg").text(title);
    $(toast).find(".toast-body").text(msg);
    new bootstrap.Toast(toast).show();
}

export function resetHtml() {
    listening.innerHTML = "";
    listenVisible = false;
    renderActions();
}

function ohNo() {
    try {
        icanhazepoch().then(sec => {
            if (Math.abs(Date.now() / 1000 - sec) > 15) {
                addToast("Time isn't synchronized", "Please synchronize your computer time to NTP servers");
            } else {
                console.log("time seems synchronized");
            }
        })
        new Date().getDay() === 3 && console.log("it's snapshot day 🐸 my dudes");
        new Date().getDate() === 1 && new Date().getMonth() === 3 && addToast("WARNING", "Your ViaVersion has expired, please renew it at https://viaversion.com/ for $99");
    } catch (e) {
        console.log(e);
    }
}
