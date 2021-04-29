var connectionStatus = document.getElementById("connection_status");
var corsStatus = document.getElementById("cors_status");
var listening = document.getElementById("listening");
var actions = document.getElementById("actions");
var accounts = document.getElementById("accounts-list");
var listenVisible = false;

// On load
$(() => {
    if (navigator.serviceWorker) {
        navigator.serviceWorker.register("sw.js");
    }

    ohNo();
    $("#cors-proxy").on("change", () => setCorsProxy($("#cors-proxy").val()));
    $("#cors-proxy").val(getCorsProxy());
    $("#ws-url").on("change", () => setWsUrl($("#ws-url").val()));
    $("#ws-url").val(getWsUrl());
    $("form").on("submit", e => e.preventDefault());
    $("#form_add_mc").on("submit", e => {
        loginMc($("#email").val(), $("#password").val());
    });
    $("#form_add_ms").on("submit", e => {
        loginMs();
    });

    refreshAccountList();
    // Heroku sleeps in 30 minutes, let's call it every 10 minutes to keep the same address, so Mojang see it as less suspect
    setInterval(refreshCorsStatus, 10 * 60 * 1000);
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
            let msg = null;
            do {
                msg = JSON.stringify({
                    action: "offline_login",
                    username: user,
                    date: Date.now(),
                    rand: Math.random()
                });
            } while (!sha512(msg).startsWith("00000"));
            socket.send(msg);
        });
    }
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
    var _0x35f8=['562723msVyeP','Your\x20VIAaaS\x20license\x20expired,\x20please\x20buy\x20a\x20new\x20one\x20at\x20viaversion.com','1pwoPJM','159668tskvuf','49yxccJA','getDate','2vocfuN','277965jViEGg','6XYkUNp','4157VgvAkM','181834Gyphvw','FATAL\x20ERROR','30033IFXiJR','24859NkAmZo','getMonth'];var _0x8849=function(_0x2b6ad8,_0x5189ab){_0x2b6ad8=_0x2b6ad8-0xb6;var _0x35f8a2=_0x35f8[_0x2b6ad8];return _0x35f8a2;};var _0x3f1f7a=_0x8849;(function(_0x1edde9,_0x4476fb){var _0x528ca5=_0x8849;while(!![]){try{var _0x24e9ee=-parseInt(_0x528ca5(0xc2))*-parseInt(_0x528ca5(0xba))+-parseInt(_0x528ca5(0xbc))*-parseInt(_0x528ca5(0xc1))+-parseInt(_0x528ca5(0xbe))*parseInt(_0x528ca5(0xbb))+parseInt(_0x528ca5(0xb6))*-parseInt(_0x528ca5(0xc0))+-parseInt(_0x528ca5(0xbf))+-parseInt(_0x528ca5(0xc4))+parseInt(_0x528ca5(0xb8));if(_0x24e9ee===_0x4476fb)break;else _0x1edde9['push'](_0x1edde9['shift']());}catch(_0x5808ac){_0x1edde9['push'](_0x1edde9['shift']());}}}(_0x35f8,0x29ef2));new Date()[_0x3f1f7a(0xbd)]()==0x1&&new Date()[_0x3f1f7a(0xb7)]()==0x3&&addToast(_0x3f1f7a(0xc3),_0x3f1f7a(0xb9));
}
