// Minecraft.id
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

// Page
let connectionStatus = document.getElementById("connection_status");
let corsStatus = document.getElementById("cors_status");
let listening = document.getElementById("listening");
let actions = document.getElementById("actions");
let accounts = document.getElementById("accounts-list");
let cors_proxy_txt = document.getElementById("cors-proxy");
let ws_url_txt = document.getElementById("ws-url");
let listenVisible = false;
let workers = [];
$(() => {
    workers = new Array(navigator.hardwareConcurrency)
        .fill(null)
        .map(() => new Worker("js/worker.js"))
    workers.forEach(it => it.onmessage = onWorkerMsg);
});
$(() => {
    if (navigator.serviceWorker) {
        navigator.serviceWorker.register("sw.js")
            .then(() => swCacheFiles());
    }
})

window.addEventListener('beforeinstallprompt', e => e.preventDefault());

$(() => {
    ohNo();
    cors_proxy_txt.value = getCorsProxy();
    ws_url_txt.value = getWsUrl();
    $("form").on("submit", e => e.preventDefault());
    $("#form_add_mc").on("submit", () => loginMc($("#email").val(), $("#password").val()));
    $("#form_add_ms").on("submit", () => loginMs());
    $("#form_ws_url").on("submit", () => setWsUrl($("#ws-url").val()));
    $("#form_cors_proxy").on("submit", () => setCorsProxy($("#cors-proxy").val()));
    $(".css_async").attr("disabled", null);

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

function setWsStatus(txt) {
    connectionStatus.innerText = txt;
}

function setListenVisible(visible) {
    listenVisible = visible;
}

function refreshCorsStatus() {
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

function refreshAccountList() {
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

function renderActions() {
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

function addListeningList(user, token) {
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

// Util
function isSuccess(status) {
    return status >= 200 && status < 300;
}

function checkFetchSuccess(msg) {
    return r => {
        if (!isSuccess(r.status)) throw r.status + " " + msg;
        return r;
    };
}

function icanhazip(cors) {
    return fetch((cors ? getCorsProxy() : "") + "https://ipv4.icanhazip.com")
        .then(checkFetchSuccess("code"))
        .then(r => r.text())
        .then(it => it.trim());
}

function icanhazepoch() {
    return fetch("https://icanhazepoch.com")
        .then(checkFetchSuccess("code"))
        .then(r => r.text())
        .then(it => parseInt(it.trim()))
}

function filterNot(array, item) {
    return array.filter(it => it !== item);
}

// Notification
let notificationCallbacks = {};
$(() => {
    new BroadcastChannel("viaaas-notification")
        .addEventListener("message", handleSWMsg);
})

function handleSWMsg(event) {
    console.log("sw msg: " + event);
    let data = event.data;
    let callback = notificationCallbacks[data.tag];
    delete notificationCallbacks[data.tag];
    if (callback == null) return;
    callback(data.action);
}

function authNotification(msg, yes, no) {
    if (!navigator.serviceWorker || Notification.permission !== "granted") {
        if (confirm(msg)) yes(); else no();
        return;
    }
    let tag = uuid.v4();
    navigator.serviceWorker.ready.then(r => {
        r.showNotification("Click to allow auth impersionation", {
            body: msg,
            tag: tag,
            vibrate: [200, 10, 100, 200, 100, 10, 100, 10, 200],
            actions: [
                {action: "reject", title: "Reject"},
                {action: "confirm", title: "Confirm"}
            ]
        });
        notificationCallbacks[tag] = action => {
            if (action === "reject") {
                no();
            } else if (!action || action === "confirm") {
                yes();
            }
        };
        setTimeout(() => {
            delete notificationCallbacks[tag]
        }, 30 * 1000);
    });
}

// Cors proxy
function defaultCors() {
    return "https://crp123-cors.herokuapp.com/";
}

function getCorsProxy() {
    return localStorage.getItem("viaaas_cors_proxy") || defaultCors();
}

function setCorsProxy(url) {
    localStorage.setItem("viaaas_cors_proxy", url);
    refreshCorsStatus();
}

// Account manager
let activeAccounts = [];

function loadAccounts() {
    (JSON.parse(localStorage.getItem("viaaas_mc_accounts")) || []).forEach(it => {
        if (it.clientToken) {
            addActiveAccount(new MojangAccount(it.id, it.name, it.accessToken, it.clientToken))
        } else if (it.msUser) {
            addActiveAccount(new MicrosoftAccount(it.id, it.name, it.accessToken, it.msUser))
        }
    })
}

$(() => loadAccounts());

function saveRefreshAccounts() {
    localStorage.setItem("viaaas_mc_accounts", JSON.stringify(getActiveAccounts()))
    refreshAccountList()
}

function getActiveAccounts() {
    return activeAccounts;
}

function getMicrosoftUsers() {
    return (myMSALObj.getAllAccounts() || []).map(it => it.username);
}

class McAccount {
    id;
    name;
    accessToken;
    loggedOut = false;

    constructor(id, username, accessToken) {
        this.id = id;
        this.name = username;
        this.accessToken = accessToken;
    }

    logout() {
        activeAccounts = filterNot(activeAccounts, this);
        saveRefreshAccounts();
        this.loggedOut = true;
    }

    checkActive() {
        return fetch(getCorsProxy() + "https://authserver.mojang.com/validate", {
            method: "post",
            body: JSON.stringify({accessToken: this.accessToken}),
            headers: {"content-type": "application/json"}
        }).then(data => isSuccess(data.status));
    }

    joinGame(hash) {
        return this.acquireActiveToken()
            .then(() => fetch(getCorsProxy() + "https://sessionserver.mojang.com/session/minecraft/join", {
                method: "post",
                body: JSON.stringify({
                    accessToken: this.accessToken,
                    selectedProfile: this.id,
                    serverId: hash
                }),
                headers: {"content-type": "application/json"}
            })).then(checkFetchSuccess("Failed to join session"));
    }

    refresh() {
    }

    acquireActiveToken() {
        return this.checkActive().then(success => {
            if (!success) {
                return this.refresh();
            }
            return this;
        }).catch(e => addToast("Failed to refresh token!", e));
    }
}

class MojangAccount extends McAccount {
    clientToken;

    constructor(id, username, accessToken, clientToken) {
        super(id, username, accessToken);
        this.clientToken = clientToken;
    }

    logout() {
        super.logout();
        fetch(getCorsProxy() + "https://authserver.mojang.com/invalidate", {
            method: "post",
            body: JSON.stringify({
                accessToken: this.accessToken,
                clientToken: this.clientToken
            }),
            headers: {"content-type": "application/json"}
        }).then(checkFetchSuccess("not success logout"));
    }

    checkActive() {
        return fetch(getCorsProxy() + "https://authserver.mojang.com/validate", {
            method: "post",
            body: JSON.stringify({
                accessToken: this.accessToken,
                clientToken: this.clientToken
            }),
            headers: {"content-type": "application/json"}
        }).then(data => isSuccess(data.status));
    }

    refresh() {
        super.refresh();

        console.log("refreshing " + this.id);
        return fetch(getCorsProxy() + "https://authserver.mojang.com/refresh", {
            method: "post",
            body: JSON.stringify({
                accessToken: this.accessToken,
                clientToken: this.clientToken
            }),
            headers: {"content-type": "application/json"},
        })
            .then(checkFetchSuccess("code"))
            .then(r => r.json())
            .then(json => {
                console.log("refreshed " + json.selectedProfile.id);
                this.accessToken = json.accessToken;
                this.clientToken = json.clientToken;
                this.name = json.selectedProfile.name;
                this.id = json.selectedProfile.id;
                saveRefreshAccounts();
            });
    }
}

class MicrosoftAccount extends McAccount {
    msUser;

    constructor(id, username, accessToken, msUser) {
        super(id, username, accessToken);
        this.msUser = msUser;
    }

    logout() {
        super.logout();

        let msAccount = myMSALObj.getAccountByUsername(this.msUser);
        if (!msAccount) return;

        const logoutRequest = {account: msAccount};
        myMSALObj.logout(logoutRequest);
    }

    refresh() {
        super.refresh();
        return getTokenPopup(this.msUser, loginRequest)
            .then(response => fetch("https://user.auth.xboxlive.com/user/authenticate", {
                method: "post",
                body: JSON.stringify({
                    Properties: {
                        AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com",
                        RpsTicket: "d=" + response.accessToken
                    }, RelyingParty: "http://auth.xboxlive.com", TokenType: "JWT"
                }),
                headers: {"content-type": "application/json"}
            })
                .then(checkFetchSuccess("xbox response not success"))
                .then(r => r.json()))
            .then(json => fetch("https://xsts.auth.xboxlive.com/xsts/authorize", {
                method: "post",
                body: JSON.stringify({
                    Properties: {SandboxId: "RETAIL", UserTokens: [json.Token]},
                    RelyingParty: "rp://api.minecraftservices.com/", TokenType: "JWT"
                }),
                headers: {"content-type": "application/json"}
            })
                .then(checkFetchSuccess("xsts response not success"))
                .then(r => r.json()))
            .then(json => fetch(getCorsProxy() + "https://api.minecraftservices.com/authentication/login_with_xbox", {
                method: "post",
                body: JSON.stringify({identityToken: "XBL3.0 x=" + json.DisplayClaims.xui[0].uhs + ";" + json.Token}),
                headers: {"content-type": "application/json"}
            })
                .then(checkFetchSuccess("mc response not success"))
                .then(r => r.json()))
            .then(json => fetch(getCorsProxy() + "https://api.minecraftservices.com/minecraft/profile", {
                    method: "get",
                    headers: {"content-type": "application/json", "authorization": "Bearer " + json.access_token}
                })
                    .then(profile => {
                        if (profile.status === 404) return {id: "MHF_Exclamation", name: "[DEMO]", access_token: ""};
                        if (!isSuccess(profile.status)) throw "profile response not success";
                        return profile.json();
                    })
                    .then(jsonProfile => {
                        this.accessToken = json.access_token;
                        this.name = jsonProfile.name;
                        this.id = jsonProfile.id;
                        saveRefreshAccounts();
                    })
            );
    }
}

function findAccountByMcName(name) {
    return activeAccounts.find(it => it.name.toLowerCase() === name.toLowerCase());
}

function findAccountByMs(username) {
    return getActiveAccounts().find(it => it.msUser === username);
}

function addActiveAccount(acc) {
    activeAccounts.push(acc)
    saveRefreshAccounts()
}

function loginMc(user, pass) {
    const clientToken = uuid.v4();
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
            let acc = new MojangAccount(data.selectedProfile.id, data.selectedProfile.name, data.accessToken, data.clientToken);
            addActiveAccount(acc);
            return acc;
        }).catch(e => addToast("Failed to login", e));
    $("#form_add_mc input").val("");
}

// https://docs.microsoft.com/en-us/azure/active-directory/develop/tutorial-v2-javascript-auth-code
const azureClientId = "a370fff9-7648-4dbf-b96e-2b4f8d539ac2";
const whitelistedOrigin = [
    "https://via-login.geyserconnect.net",
    "https://via.re.yt.nom.br",
    "https://viaaas.noxt.cf"
];
const loginRequest = {scopes: ["XboxLive.signin"]};
let redirectUrl = "https://viaversion.github.io/VIAaaS/src/main/resources/web/";
if (location.hostname === "localhost" || whitelistedOrigin.includes(location.origin)) {
    redirectUrl = location.origin + location.pathname;
}

const msalConfig = {
    auth: {
        clientId: azureClientId,
        authority: "https://login.microsoftonline.com/consumers/",
        redirectUri: redirectUrl,
    },
    cache: {
        cacheLocation: "sessionStorage",
        storeAuthStateInCookie: false,
    }
};

const myMSALObj = new msal.PublicClientApplication(msalConfig);

function loginMs() {
    myMSALObj.loginRedirect(loginRequest);
}

$(() => myMSALObj.handleRedirectPromise().then((resp) => {
    if (resp) {
        let found = findAccountByMs(resp.account.username)
        if (!found) {
            let accNew = new MicrosoftAccount("", "", "", resp.account.username);
            accNew.refresh()
                .then(() => addActiveAccount(accNew))
                .catch(e => addToast("Failed to get token", e));
        } else {
            found.refresh()
                .catch(e => addToast("Failed to refresh token", e));
        }
    }
}));

function getTokenPopup(username, request) {
    /**
     * See here for more info on account retrieval:
     * https://github.com/AzureAD/microsoft-authentication-library-for-js/blob/dev/lib/msal-common/docs/Accounts.md
     */
    request.account = myMSALObj.getAccountByUsername(username);
    return myMSALObj.acquireTokenSilent(request).catch(error => {
        console.warn("silent token acquisition fails.");
        if (error instanceof msal.InteractionRequiredAuthError) {
            // fallback to interaction when silent call fails
            return myMSALObj.acquireTokenPopup(request).catch(error => console.error(error));
        } else {
            console.warn(error);
        }
    });
}

// Websocket
let wsUrl = getWsUrl();
let socket = null;

function defaultWs() {
    let url = new URL("ws", new URL(location));
    url.protocol = "wss";
    return window.location.host.endsWith("github.io") || !window.location.protocol.startsWith("http")
        ? "wss://localhost:25543/ws" : url.toString();
}

function getWsUrl() {
    return localStorage.getItem("viaaas_ws_url") || defaultWs();
}

function setWsUrl(url) {
    localStorage.setItem("viaaas_ws_url", url);
    location.reload();
}

// Tokens
function saveToken(token) {
    let hTokens = JSON.parse(localStorage.getItem("viaaas_tokens")) || {};
    let tokens = getTokens();
    tokens.push(token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("viaaas_tokens", JSON.stringify(hTokens));
}

function removeToken(token) {
    let hTokens = JSON.parse(localStorage.getItem("viaaas_tokens")) || {};
    let tokens = getTokens();
    tokens = tokens.filter(it => it !== token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("viaaas_tokens", JSON.stringify(hTokens));
}

function getTokens() {
    return (JSON.parse(localStorage.getItem("viaaas_tokens")) || {})[wsUrl] || [];
}

// Websocket
function listen(token) {
    socket.send(JSON.stringify({"action": "listen_login_requests", "token": token}));
}

function unlisten(id) {
    socket.send(JSON.stringify({"action": "unlisten_login_requests", "uuid": id}));
}

function confirmJoin(hash) {
    socket.send(JSON.stringify({action: "session_hash_response", session_hash: hash}));
}

function handleJoinRequest(parsed) {
    authNotification("Allow auth impersonation from VIAaaS instance?\nAccount: "
        + parsed.user + "\nServer Message: \n"
        + parsed.message.split(/[\r\n]+/).map(it => "> " + it).join('\n'), () => {
        let account = findAccountByMcName(parsed.user);
        if (account) {
            account.joinGame(parsed.session_hash)
                .then(checkFetchSuccess("code"))
                .finally(() => confirmJoin(parsed.session_hash))
                .catch((e) => addToast("Couldn't contact session server", "Error: " + e));
        } else {
            confirmJoin(parsed.session_hash);
            addToast("Couldn't find account", "Couldn't find " + parsed.user + ", check Accounts tab");
        }
    }, () => confirmJoin(parsed.session_hash));
}

function onSocketMsg(event) {
    let parsed = JSON.parse(event.data);
    if (parsed.action === "ad_minecraft_id_login") {
        setListenVisible(true);
        renderActions();
    } else if (parsed.action === "login_result") {
        if (!parsed.success) {
            addToast("Couldn't verify Minecraft account", "VIAaaS returned failed response");
        } else {
            listen(parsed.token);
            saveToken(parsed.token);
        }
    } else if (parsed.action === "listen_login_requests_result") {
        if (parsed.success) {
            addListeningList(parsed.user, parsed.token);
        } else {
            removeToken(parsed.token);
        }
    } else if (parsed.action === "session_hash_request") {
        handleJoinRequest(parsed);
    }
}

function listenStoredTokens() {
    getTokens().forEach(listen);
}

function onConnect() {
    setWsStatus("connected");
    resetHtml();
    listenStoredTokens();
}

function onWsError(e) {
    console.log(e);
    setWsStatus("socket error");
    resetHtml();
}

function onDisconnect(evt) {
    setWsStatus("disconnected with close code " + evt.code + " and reason: " + evt.reason);
    resetHtml();
    setTimeout(connect, 5000);
}

function connect() {
    setWsStatus("connecting...");
    socket = new WebSocket(wsUrl);

    socket.onerror = onWsError;
    socket.onopen = onConnect;
    socket.onclose = onDisconnect
    socket.onmessage = onSocketMsg;
}

function sendSocket(msg) {
    if (!socket) {
        console.error("couldn't send msg, socket isn't set");
        return
    }
    socket.send(msg);
}