// SW
if (navigator.serviceWorker) {
    navigator.serviceWorker.register("sw.js");
}

// Minecraft.id
let urlParams = new URLSearchParams();
window.location.hash.substr(1).split("?").map(it => new URLSearchParams(it).forEach((a, b) => urlParams.append(b, a)));
var mcIdUsername = urlParams.get("username");
var mcauth_code = urlParams.get("mcauth_code");
if (urlParams.get("mcauth_success") == "false") {
    addToast("Couldn't authenticate with Minecraft.ID", urlParams.get("mcauth_msg"));
}

// WS url
function defaultWs() {
    let url = new URL("ws", new URL(location));
    url.protocol = "wss";
    return window.location.host == "viaversion.github.io" || !window.location.host ? "wss://localhost:25543/ws" : url.toString();
}
function getWsUrl() {
    let url = localStorage.getItem("ws-url") || defaultWs();
    localStorage.setItem("ws-url", url);
    return url;
}

var wsUrl = getWsUrl();
var socket = null;
var connectionStatus = document.getElementById("connection_status");
var corsStatus = document.getElementById("cors_status");
var listening = document.getElementById("listening");
var actions = document.getElementById("actions");
var accounts = document.getElementById("accounts-list");
var listenVisible = false;
var pageBlur = false;
document.addEventListener("focus", () => {
    pageBlur = false;
});
document.addEventListener("blur", () => {
    pageBlur = true;
});

// Util
isMojang = it => !!it.clientToken;
isNotMojang = it => !it.clientToken;
isSuccess = status => status >= 200 && status < 300;
checkFetchSuccess = msg => r => {
    if (!isSuccess(r.status)) throw r.status + " " + msg;
    return r;
};
function icanhazip(cors) {
    return fetch((cors ? getCorsProxy() : "") + "https://ipv4.icanhazip.com").then(checkFetchSuccess("code"))
        .then(r => r.text()).then(it => it.trim());
}

// Proxy
function defaultCors() {
    return "https://crp123-cors.herokuapp.com/";
}
function getCorsProxy() {
    return localStorage.getItem("cors-proxy") || defaultCors();
}
function setCorsProxy(url) {
    localStorage.setItem("cors-proxy", url);
    refreshCorsStatus();
}

// Tokens
function saveToken(token) {
    let hTokens = JSON.parse(localStorage.getItem("tokens")) || {};
    let tokens = hTokens[wsUrl] || [];
    tokens.push(token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("tokens", JSON.stringify(hTokens));
}
function removeToken(token) {
    let hTokens = JSON.parse(localStorage.getItem("tokens")) || {};
    let tokens = hTokens[wsUrl] || [];
    tokens = tokens.filter(it => it != token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("tokens", JSON.stringify(hTokens));
}
function getTokens() {
    return (JSON.parse(localStorage.getItem("tokens")) || {})[wsUrl] || [];
}

// Accounts
function storeMcAccount(accessToken, clientToken, name, id, msUser = null) {
    let accounts = JSON.parse(localStorage.getItem("mc_accounts")) || [];
    let account = {accessToken: accessToken, clientToken: clientToken, name: name, id: id, msUser: msUser};
    accounts.push(account);
    localStorage.setItem("mc_accounts", JSON.stringify(accounts));
    refreshAccountList();
    return account;
}
function removeMcAccount(id) {
    let accounts = JSON.parse(localStorage.getItem("mc_accounts")) || [];
    accounts = accounts.filter(it => it.id != id);
    localStorage.setItem("mc_accounts", JSON.stringify(accounts));
    refreshAccountList();
}
function getMcAccounts() {
    return JSON.parse(localStorage.getItem("mc_accounts")) || [];
}
function findAccountByMcName(name) {
    return getMcAccounts().reverse().find(it => it.name.toLowerCase() == name.toLowerCase());
}
function findAccountByMs(username) {
    return getMcAccounts().filter(isNotMojang).find(it => it.msUser == username);
}

// Mojang account
function loginMc(user, pass) {
    var clientToken = uuid.v4();
    fetch(getCorsProxy() + "https://authserver.mojang.com/authenticate", {
        method: "post",
        body: JSON.stringify({
            agent: {name: "Minecraft", version: 1},
            username: user,
            password: pass,
            clientToken: clientToken,
        }),
        headers: {"content-type": "application/json"}
    }).then(checkFetchSuccess("code"))
    .then(r => r.json())
    .then(data => {
        storeMcAccount(data.accessToken, data.clientToken, data.selectedProfile.name, data.selectedProfile.id);
    }).catch(e => addToast("Failed to login", e));
    $("#form_add_mc input").val("");
}
function logoutMojang(id) {
    getMcAccounts().filter(isMojang).filter(it => it.id == id).forEach(it => {
        fetch(getCorsProxy() + "https://authserver.mojang.com/invalidate", {method: "post",
            body: JSON.stringify({
                accessToken: it.accessToken,
                clientToken: it.clientToken
            }),
            headers: {"content-type": "application/json"}
        })
        .then(checkFetchSuccess("not success logout"))
        .then(data => removeMcAccount(id))
        .catch(e => {
            if (confirm("failed to invalidate token! error: " + e + " remove account?")) {
                removeMcAccount(id);
            }
        });
    });
}
function refreshMojangAccount(it) {
    console.log("refreshing " + it.id);
    return fetch(getCorsProxy() + "https://authserver.mojang.com/refresh", {
        method: "post",
        body: JSON.stringify({
            accessToken: it.accessToken,
            clientToken: it.clientToken
        }),
        headers: {"content-type": "application/json"},
    }).then(checkFetchSuccess("code"))
    .then(r => r.json())
    .then(json => {
        console.log("refreshed " + json.selectedProfile.id);
        removeMcAccount(json.selectedProfile.id);
        return storeMcAccount(json.accessToken, json.clientToken, json.selectedProfile.name, json.selectedProfile.id);
    });
}

// Minecraft api
function getMcUserToken(account) {
    return validateToken(account).then(data => {
        if (!isSuccess(data.status)) {
            if (isMojang(account)) {
                return refreshMojangAccount(account);
            } else {
                return refreshTokenMs(account.msUser);
            }
        }
        return account;
    }).catch(e => addToast("Failed to refresh token!", e));
}
function validateToken(account) {
    return fetch(getCorsProxy() + "https://authserver.mojang.com/validate", {method: "post",
        body: JSON.stringify({
            accessToken: account.accessToken,
            clientToken: account.clientToken || undefined
        }),
        headers: {"content-type": "application/json"}
    });
}
function joinGame(token, id, hash) {
    return fetch(getCorsProxy() + "https://sessionserver.mojang.com/session/minecraft/join", {
        method: "post",
        body: JSON.stringify({
            accessToken: token,
            selectedProfile: id,
            serverId: hash
        }),
        headers: {"content-type": "application/json"}
    });
}

// html
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
    head.src = "https://crafthead.net/helm/" + id;
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
            socket.send(JSON.stringify({action: "offline_login", username: user}));
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
    let msg = document.createElement("p");
    msg.innerText = "Listening to login: " + user;
    listening.appendChild(msg);
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

// Notification
var notificationCallbacks = {};
function handleSWMsg(event) {
    console.log("sw msg: " + event);
    let data = event.data;
    let callback = notificationCallbacks[data.tag];
    delete notificationCallbacks[data.tag];
    if (callback == null) return;
    callback(data.action);
}
function authNotification(msg, yes, no) {
    if (!navigator.serviceWorker || (!pageBlur && !document.hidden) || Notification.permission != "granted") {
        if (confirm(msg)) yes(); else no();
        return;
    }
    let tag = uuid.v4();
    navigator.serviceWorker.ready.then(r => {
        r.showNotification("Click to allow auth impersionation", {
            body: msg,
            tag: tag,
            vibrate: [200,10,100,200,100,10,100,10,200],
            actions: [
                {action: "reject", title: "Reject"},
                {action: "confirm", title: "Confirm"}
            ]
        });
        notificationCallbacks[tag] = action => {
            if (action == "reject") {
                no();
            } else if (!action || action == "confirm") {
                yes();
            } else {
                return;
            }
        };
        setTimeout(() => { delete notificationCallbacks[tag] }, 30_000);
    });
}
new BroadcastChannel("viaaas-notification").addEventListener("message", handleSWMsg);

// Websocket
function listen(token) {
    socket.send(JSON.stringify({"action": "listen_login_requests", "token": token}));
}
function confirmJoin(hash) {
    socket.send(JSON.stringify({action: "session_hash_response", session_hash: hash}));
}
function handleJoinRequest(parsed) {
    authNotification("Allow auth impersonation from VIAaaS instance?\nUsername: " + parsed.user + "\nSession Hash: " + parsed.session_hash + "\nServer Message: '" + parsed.message + "'", () => {
        let account = findAccountByMcName(parsed.user);
        if (account) {
            getMcUserToken(account).then(data => {
                return joinGame(data.accessToken, data.id, parsed.session_hash);
            })
            .then(checkFetchSuccess("code"))
            .finally(() => confirmJoin(parsed.session_hash))
            .catch((e) => addToast("Couldn't contact session server", "error: " + e));
        } else {
            confirmJoin(parsed.session_hash);
            addToast("Couldn't find account", parsed.user);
        }
    }, () => confirmJoin(parsed.session_hash));
}
function onSocketMsg(event) {
    let parsed = JSON.parse(event.data);
    if (parsed.action == "ad_minecraft_id_login") {
        listenVisible = true;
        renderActions();
    } else if (parsed.action == "login_result") {
        if (!parsed.success) {
            addToast("Couldn't verify Minecraft account", "VIAaaS returned failed response");
        } else {
            listen(parsed.token);
            saveToken(parsed.token);
        }
    } else if (parsed.action == "listen_login_requests_result") {
        if (parsed.success) {
            addListeningList(parsed.user);
        } else {
            removeToken(parsed.token);
        }
    } else if (parsed.action == "session_hash_request") {
        handleJoinRequest(parsed);
    }
}
function listenStoredTokens() {
    getTokens().forEach(listen);
}
function onConnect() {
    connectionStatus.innerText = "connected";
    resetHtml();
    listenStoredTokens();
}
function onWsError(e) {
    console.log(e);
    connectionStatus.innerText = "socket error";
    resetHtml();
}
function onDisconnect(evt) {
    connectionStatus.innerText = "disconnected with close code " + evt.code + " and reason: " + evt.reason;
    resetHtml();
    setTimeout(connect, 5000);
}
function connect() {
    connectionStatus.innerText = "connecting...";
    socket = new WebSocket(wsUrl);

    socket.onerror = onWsError;
    socket.onopen = onConnect;
    socket.onclose = onDisconnect
    socket.onmessage = onSocketMsg;
}

// On load
$(() => {
    $("#cors-proxy").on("change", () => setCorsProxy($("#cors-proxy").val()));
    $("#cors-proxy").val(getCorsProxy());
    $("#ws-url").on("change", () => {
       localStorage.setItem("ws-url", $("#ws-url").val());
       location.reload();
    });
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
