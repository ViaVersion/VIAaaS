var connectionStatus = document.getElementById("connection_status");
var corsStatus = document.getElementById("cors_status");
var listening = document.getElementById("listening");
var actions = document.getElementById("actions");
var accounts = document.getElementById("accounts-list");
var listenVisible = false;
var workers = [];
$(() => workers = new Array(navigator.hardwareConcurrency).fill(null).map(() => new Worker("js/worker.js")));
window.addEventListener('beforeinstallprompt', e => {
  e.preventDefault();
});

// On load
$(() => {
    if (navigator.serviceWorker) {
        navigator.serviceWorker.register("sw.js");
    }

    ohNo();
    $("#cors-proxy").val(getCorsProxy());
    $("#ws-url").val(getWsUrl());
    $("form").on("submit", e => e.preventDefault());
    $("#form_add_mc").on("submit", e => loginMc($("#email").val(), $("#password").val()));
    $("#form_add_ms").on("submit", e => loginMs());
    $("#form_ws_url").on("submit", e => setWsUrl($("#ws-url").val()));
    $("#form_cors_proxy").on("submit", e => setCorsProxy($("#cors-proxy").val()));

    workers.forEach(it => it.onmessage = onWorkerMsg);
    refreshAccountList();
    setInterval(refreshCorsStatus, 10 * 60 * 1000); // Heroku auto sleeps in 30 min
    refreshCorsStatus();
    resetHtml();

    connect();
});

function refreshCorsStatus() {
    corsStatus.innerText = "...";
    icanhazip(true).then(ip => {
            return icanhazip(false).then(ip2 => corsStatus.innerText = "OK " + ip + (ip != ip2 ? " (different IP)" : ""));
        }).catch(e => corsStatus.innerText = "error: " + e);
}

function addMcAccountToList(id, name, msUser = null) {
    let p = document.createElement("p");
    let head = document.createElement("img");
    let n = document.createElement("span");
    let remove = document.createElement("a");
    n.innerText = " " + name + " " + (msUser == null ? "" : "(" + msUser + ") ");
    remove.innerText = "Logout";
    remove.href = "javascript:";
    remove.onclick = () => {
        if (msUser == null) {
            logoutMojang(id);
        } else {
            logoutMs(msUser);
        }
    };
    head.className = "account_head";
    head.alt = name + "'s head";
    head.src = (id.length == 36 || id.length == 32) ? "https://crafatar.com/avatars/" + id + "?overlay" : "https://crafthead.net/helm/" + id;
    p.append(head);
    p.append(n);
    p.append(remove);
    accounts.appendChild(p);
}

function refreshAccountList() {
    accounts.innerHTML = "";
    getMcAccounts().filter(isMojang).sort((a, b) => a.name.localeCompare(b.name)).forEach(it => addMcAccountToList(it.id, it.name));
    (myMSALObj.getAllAccounts() || []).sort((a, b) => a.username.localeCompare(b.username)).forEach(msAccount => {
        let mcAcc = findAccountByMs(msAccount.username) || {id: "MHF_Question", name: "..."};
        addMcAccountToList(mcAcc.id, mcAcc.name, msAccount.username);
    });
}

function renderActions() {
    actions.innerHTML = "";
    if (Notification.permission == "default") {
        actions.innerHTML += '<p><a href="javascript:" id="notificate">Enable notifications</a></p>';
        $("#notificate").on("click", e => Notification.requestPermission().then(renderActions)); // i'm lazy
    }
    if (listenVisible) {
        if (mcIdUsername != null && mcauth_code != null) {
            addAction("Listen to " + mcIdUsername, () => {
                socket.send(JSON.stringify({
                    "action": "minecraft_id_login",
                    "username": mcIdUsername,
                    "code": mcauth_code}));
                mcauth_code = null;
                renderActions();
            });
        }
        addAction("Listen to premium login in VIAaaS instance", () => {
            let user = prompt("Premium username (case-sensitive): ", "");
            if (!user) return;
            let callbackUrl = new URL(location);
            callbackUrl.search = "";
            callbackUrl.hash = "#username=" + encodeURIComponent(user);
            location = "https://api.minecraft.id/gateway/start/" + encodeURIComponent(user)
                + "?callback=" + encodeURIComponent(callbackUrl);
        });
        addAction("Listen to offline login in VIAaaS instance", () => {
            let user = prompt("Offline username (case-sensitive):", "");
            if (!user) return;
            workers.forEach(it => it.postMessage({action: "listen_pow", user: user, id: Math.random()}));
            addToast("Offline username", "Please wait a minute...");
        });
    }
}

function onWorkerMsg(e) {
    console.log(e);
    if (e.data.action == "completed_pow") onCompletedPoW(e);
}

function onCompletedPoW(e) {
    addToast("Offline username", "Completed proof of work");
    workers.forEach(it => it.postMessage({action: "cancel", id: e.data.id}));
    socket.send(e.data.msg);
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

function addListeningList(user) {
    let p = document.createElement("p");
    let head = document.createElement("img");
    let n = document.createElement("span");
    let remove = document.createElement("a");
    n.innerText = " " + user + " ";
    remove.innerText = "Unlisten";
    remove.href = "javascript:";
    remove.onclick = () => {
        // todo remove the token
        listening.removeChild(p);
        unlisten(user);
    };
    head.className = "account_head";
    head.alt = user + "'s head";
    head.src = "https://crafthead.net/helm/" + user;
    p.append(head);
    p.append(n);
    p.append(remove);
    listening.appendChild(p);
}

function addToast(title, msg) {
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

function resetHtml() {
    listening.innerHTML = "";
    listenVisible = false;
    renderActions();
}

function ohNo() {
    new Date().getDay() == 3 && console.log("it's snapshot day 🐸 my dudes"); new Date().getDate() == 1 && new Date().getMonth() == 3 && addToast("WARNING", "Your ViaVersion has expired, please renew it at https://viaversion.com/ for $99");
}
