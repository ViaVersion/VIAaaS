"use strict";
/// <reference path='config.ts' />
// Note that some APIs only work on HTTPS
// Minecraft.id
let urlParams = new URLSearchParams();
window.location.hash.substring(1).split("?")
    .map(it => new URLSearchParams(it)
    .forEach((a, b) => urlParams.append(b, a)));
let mcIdUsername = urlParams.get("username");
let mcIdCode = urlParams.get("mcauth_code");
let mcIdSuccess = urlParams.get("mcauth_success");
$(() => {
    var _a;
    if (mcIdSuccess === "false") {
        addToast("Couldn't authenticate with Minecraft.ID", (_a = urlParams.get("mcauth_msg")) !== null && _a !== void 0 ? _a : "");
    }
    if (mcIdCode != null) {
        history.replaceState(null, "", "#");
    }
});
// Page
let connectionStatus = document.getElementById("connection_status");
let corsStatus = document.getElementById("cors_status");
let listening = document.getElementById("listening");
let accounts = document.getElementById("accounts-list");
let cors_proxy_txt = document.getElementById("cors-proxy");
let ws_url_txt = document.getElementById("ws-url");
let instance_suffix_input = document.getElementById("instance_suffix");
let listenVisible = false;
// + deltaTime means that the clock is ahead
let deltaTime = 0;
let workers = [];
$(() => {
    workers = new Array(navigator.hardwareConcurrency)
        .fill(null)
        .map(() => new Worker("js/worker.js"));
    workers.forEach(it => it.onmessage = onWorkerMsg);
});
$(() => {
    if (navigator.serviceWorker) {
        navigator.serviceWorker.register("sw.js").catch(e => {
            console.log(e);
            addToast("Failed to install service worker", e.toString());
        });
    }
});
$(() => {
    $(".async-css").attr("rel", "stylesheet");
    $("form").on("submit", e => e.preventDefault());
    $("a[href='javascript:']").on("click", e => e.preventDefault());
    $("[data-bs-toggle='tooltip']").get().forEach(it => {
        new bootstrap.Tooltip(it);
    });
    cors_proxy_txt.value = getCorsProxy();
    ws_url_txt.value = getWsUrl();
    instance_suffix_input.defaultValue = getDefaultInstanceSuffix();
    if (location.host.endsWith("github.io")) {
        instance_suffix_input.readOnly = false;
        instance_suffix_input.disabled = false;
    }
    $("#form_add_ms").on("submit", () => loginMs());
    $("#form_ws_url").on("submit", () => setWsUrl($("#ws-url").val()));
    $("#form_cors_proxy").on("submit", () => setCorsProxy($("#cors-proxy").val()));
    $("#form_listen").on("submit", () => submittedListen());
    $("#form_send_token").on("submit", () => submittedSendToken());
    $("#en_notifications").on("click", () => Notification.requestPermission().then(renderActions));
    $("#listen_continue").on("click", () => clickedListenContinue());
    $("#address_info_form").on("input", () => generateAddress());
    $("#generated_address").on("click", () => copyGeneratedAddress());
    $(window).on("beforeinstallprompt", e => e.preventDefault());
    ohNo();
    generateAddress();
    refreshAccountList();
    setInterval(refreshCorsStatus, 10 * 60 * 1000);
    refreshCorsStatus();
    resetHtml();
});
$(() => {
    connect();
});
function setWsStatus(txt) {
    connectionStatus.innerText = txt;
}
function refreshCorsStatus() {
    corsStatus.innerText = "...";
    getIpAddress(true).then(ip => {
        return getIpAddress(false).then(ip2 => corsStatus.innerText = "OK " + ip + (ip !== ip2 ? " (different IP)" : ""));
    }).catch(e => corsStatus.innerText = "error: " + e);
}
function addMcAccountToList(account) {
    let line = $(`<li class='input-group d-flex'>
    <span class='input-group-text'><img alt="?" src="?" loading="lazy" width=24 class='mc-head'/></span>
    <span class='form-control mc-user'></span>
    <button type="button" class='btn btn-danger mc-remove'>Logout</button>
    </li>`);
    let txt = account.name;
    line.find(".mc-user").text(txt);
    line.find(".mc-remove").on("click", () => account.logout());
    let head = line.find(".mc-head");
    head.attr("alt", account.name + "'s head");
    head.attr("src", "https://crafthead.net/helm/" + account.id);
    $(accounts).append(line);
}
function addUsernameList(username) {
    let line = $("<option class='mc_username'></option>");
    line.text(username);
    $("#send_token_user").append(line);
    $("#backend_user_list").append(line.clone());
}
function refreshAccountList() {
    accounts.innerHTML = "";
    $("#send_token_user .mc_username").remove();
    $("#backend_user_list .mc_username").remove();
    getActiveAccounts()
        .sort((a, b) => a.name.localeCompare(b.name))
        .forEach(it => {
        addMcAccountToList(it);
        addUsernameList(it.name);
    });
}
function copyGeneratedAddress() {
    navigator.clipboard.writeText($("#generated_address").text()).catch(e => console.log(e));
}
function generateAddress() {
    var _a, _b;
    let backAddress = $("#connect_address").val();
    try {
        let url = new URL("https://" + backAddress);
        let finalAddress = "";
        let host = url.hostname;
        let version = (_a = $("#connect_version").val()) === null || _a === void 0 ? void 0 : _a.toString().replaceAll(".", "_");
        let username = $("#connect_user").val();
        let onlineMode = (_b = $("#connect_online").val()) === null || _b === void 0 ? void 0 : _b.toString();
        if (host.includes(":") || host.includes("[")) {
            host = host.replaceAll(":", "-")
                .replaceAll(/[\[\]]/g, "") + ".sslip.io";
        }
        finalAddress += host;
        if (url.port)
            finalAddress += "._p" + url.port;
        if (version)
            finalAddress += "._v" + version;
        if (username)
            finalAddress += "._u" + username;
        if (onlineMode)
            finalAddress += "._o" + onlineMode.substring(0, 1);
        finalAddress += "." + $("#instance_suffix").val();
        $("#generated_address").text(finalAddress);
    }
    catch (e) {
        console.log(e);
        $("#generated_address").text("");
    }
}
$("#mcIdUsername").text(mcIdUsername !== null && mcIdUsername !== void 0 ? mcIdUsername : "");
function submittedListen() {
    let user = $("#listen_username").val();
    if (!user)
        return;
    if ($("#listen_online")[0].checked) {
        let callbackUrl = new URL(location.href);
        callbackUrl.search = "";
        callbackUrl.hash = "#username=" + encodeURIComponent(user);
        location.href = "https://api.minecraft.id/gateway/start/" + encodeURIComponent(user)
            + "?callback=" + encodeURIComponent(callbackUrl.toString());
    }
    else {
        let taskId = Math.random();
        workers.forEach(it => it.postMessage({ action: "listen_pow", user: user, id: taskId, deltaTime: deltaTime }));
        addToast("Offline username", "Please wait a minute...");
    }
}
function submittedSendToken() {
    let account = findAccountByMcName($("#send_token_user").val());
    if (!account)
        return;
    account.acquireActiveToken()
        .then(() => {
        sendSocket(JSON.stringify({
            "action": "save_access_token",
            "mc_access_token": account === null || account === void 0 ? void 0 : account.accessToken
        }));
    })
        .catch(e => addToast("Failed to send access token", e));
}
function clickedListenContinue() {
    sendSocket(JSON.stringify({
        "action": "minecraft_id_login",
        "username": mcIdUsername,
        "code": mcIdCode
    }));
    mcIdCode = null;
    renderActions();
}
function renderActions() {
    $("#en_notifications").hide();
    $("#listen_continue").hide();
    $("#listen_open").hide();
    $("#send_token_open").hide();
    if (Notification.permission === "default") {
        $("#en_notifications").show();
    }
    if (listenVisible) {
        if (mcIdUsername != null && mcIdCode != null) {
            $("#listen_continue").show();
        }
        $("#listen_open").show();
        $("#send_token_open").show();
    }
}
function onWorkerMsg(e) {
    if (e.data.action === "completed_pow")
        onCompletedPoW(e);
}
function onCompletedPoW(e) {
    addToast("Offline username", "Completed proof of work");
    workers.forEach(it => it.postMessage({ action: "cancel", id: e.data.id }));
    sendSocket(e.data.msg);
}
function addListeningList(userId, username, token) {
    let line = $(`<li class='list-group-item d-flex justify-content-between align-items-center p-1'>
        <span class='ms-2'><img alt='?' src='?' loading='lazy' width=24 class='mc-head'/></span>
        <span class='username'></span>
        <button class='btn btn-danger' type='button'>Unlisten</button>
    </li>`);
    let displayName = username || userId;
    line.find(".username").text(displayName);
    line.find(".btn").on("click", () => {
        removeToken(token);
        line.remove();
        unlisten(userId);
    });
    let head = line.find(".mc-head");
    head.attr("alt", displayName + "'s head");
    head.attr("src", "https://crafthead.net/helm/" + userId);
    $(listening).append(line);
}
function addToast(title, msg, yes = null, no = null) {
    let toast = $(`<div class="toast" role="alert" aria-live="assertive" aria-atomic="true">
 <div class="toast-header">
   <strong class="me-auto toast_title_msg"></strong>
   <button type="button" class="btn-close" data-bs-dismiss="toast" aria-label="Close"></button>
 </div>
 <div class="toast-body">
   <pre class="txt text-wrap"></pre>
   <div class="btns mt-2 pt-2 border-top"></div>
 </div>
</div>`);
    toast.find(".toast_title_msg").text(title);
    let tBody = toast.find(".toast-body");
    tBody.find(".txt").text(msg);
    let btns = $(tBody).find(".btns");
    let hasButtons = false;
    if (yes != null) {
        hasButtons = true;
        let btn = $("<button type='button' data-bs-dismiss='toast' class='btn btn-primary btn-sm'>Yes</button>");
        btn.on("click", yes);
        btns.append(btn);
    }
    if (no != null) {
        hasButtons = true;
        let btn = $("<button type='button' data-bs-dismiss='toast' class='btn btn-secondary btn-sm'>No</button>");
        btn.on("click", no);
        btns.append(btn);
    }
    if (!hasButtons) {
        btns.addClass("d-none");
    }
    $("#toasts").prepend(toast);
    new bootstrap.Toast(toast[0]).show();
}
function resetHtml() {
    listening.innerHTML = "";
    listenVisible = false;
    renderActions();
}
function ohNo() {
    try {
        getNetworkTimestamp().then(sec => {
            const calcDelta = Date.now() - sec * 1000;
            if (Math.abs(calcDelta) > 10000) {
                addToast("Time isn't synchronized", "Please synchronize your computer time to NTP servers");
                deltaTime = calcDelta;
                console.log("applying delta time " + deltaTime);
            }
            else {
                console.log("time seems synchronized");
            }
        });
        try {
            new BroadcastChannel("test");
        }
        catch (e) {
            addToast("Unsupported browser", "This browser doesn't support required APIs");
        }
        new Date().getDay() === 3 && console.log("it's snapshot day 🐸 my dudes");
        new Date().getDate() === 1 && new Date().getMonth() === 3 && addToast("LICENSE EXPIRED", "Your ViaVersion has expired, please renew it at https://viaversion.com/ for only $99");
    }
    catch (e) {
        console.log(e);
    }
}
// Util
function checkFetchSuccess(msg) {
    return (r) => {
        if (!r.ok)
            throw r.status + " " + msg;
        return r;
    };
}
async function getIpAddress(cors) {
    return fetch((cors ? getCorsProxy() : "") + "https://ipv4.icanhazip.com")
        .then(checkFetchSuccess("code"))
        .then(r => r.text())
        .then(it => it.trim());
}
function getNetworkTimestamp() {
    return fetch("api/getEpoch", { "headers": { "accept": "application/json" } })
        .then(checkFetchSuccess("code"))
        .then(r => r.json())
        .then(it => parseInt(it));
}
// Notification
let notificationCallbacks = new Map();
$(() => {
    new BroadcastChannel("viaaas-notification").addEventListener("message", handleSWMsg);
});
function handleSWMsg(event) {
    console.log("sw msg: ", event);
    let data = event.data;
    let callback = notificationCallbacks.get(data.tag);
    notificationCallbacks.delete(data.tag);
    if (callback == null)
        return;
    callback(data.action);
}
function authNotification(msg, yes, no) {
    if (!navigator.serviceWorker || Notification.permission !== "granted") {
        addToast("Allow auth impersonation?", msg, yes, no);
        return;
    }
    // @ts-ignore
    let tag = uuid.v4();
    navigator.serviceWorker.ready.then(r => {
        r.showNotification("Click to allow auth impersonation", {
            body: msg,
            tag: tag,
            vibrate: [200, 10, 100, 200, 100, 10, 100, 10, 200],
            actions: [
                { action: "reject", title: "Reject" },
                { action: "confirm", title: "Confirm" }
            ]
        }).then(() => {
        });
        notificationCallbacks.set(tag, action => {
            if (action === "reject") {
                no();
            }
            else if (!action || action === "confirm") {
                yes();
            }
        });
        setTimeout(() => {
            notificationCallbacks.delete(tag);
        }, 30 * 1000);
    });
}
// Cors proxy
function defaultCors() {
    return self.defaultCorsProxy || "https://cors.re.yt.nom.br/";
}
function getDefaultInstanceSuffix() {
    return self.defaultInstanceSuffix || location.hostname;
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
    let serialized = localStorage.getItem("viaaas_mc_accounts");
    let parsed = serialized ? JSON.parse(serialized) : [];
    parsed.forEach((it) => {
        if (it.clientToken) {
            // Mojang auth doesn't work on multiplayer anymore
        }
        else if (it.msUser && myMSALObj.getAccountByUsername(it.msUser)) {
            addActiveAccount(new MicrosoftAccount(it.id, it.name, it.accessToken, it.msUser));
        }
    });
}
$(() => loadAccounts());
function saveRefreshAccounts() {
    localStorage.setItem("viaaas_mc_accounts", JSON.stringify(getActiveAccounts()));
    refreshAccountList();
}
function getActiveAccounts() {
    return activeAccounts;
}
class McAccount {
    constructor(id, username, accessToken) {
        this.id = id;
        this.name = username;
        this.accessToken = accessToken;
        this.loggedOut = false;
    }
    async logout() {
        activeAccounts = activeAccounts.filter(it => it !== this);
        saveRefreshAccounts();
        this.loggedOut = true;
    }
    async checkActive() {
        return true;
    }
    async joinGame(hash) {
        await this.acquireActiveToken()
            .then(() => fetch(getCorsProxy() + "https://sessionserver.mojang.com/session/minecraft/join", {
            method: "post",
            body: JSON.stringify({
                accessToken: this.accessToken,
                selectedProfile: this.id,
                serverId: hash
            }),
            headers: { "content-type": "application/json" }
        }))
            .then(checkFetchSuccess("Failed to join session"));
    }
    async refresh() {
    }
    async acquireActiveToken() {
        return this.checkActive()
            .then(success => {
            if (!success) {
                return this.refresh().then(() => {
                });
            }
            return Promise.resolve();
        })
            .catch(e => addToast("Failed to refresh mc token!", e));
    }
}
class MicrosoftAccount extends McAccount {
    constructor(id, username, accessToken, msUser) {
        super(id, username, accessToken);
        this.msUser = msUser;
    }
    async logout() {
        await super.logout();
        let msAccount = myMSALObj.getAccountByUsername(this.msUser);
        if (!msAccount)
            return;
        const logoutRequest = { account: msAccount };
        await myMSALObj.logoutPopup(logoutRequest);
    }
    async refresh() {
        let msTokenResp = await getTokenPopup(this.msUser, getLoginRequest());
        // noinspection HttpUrlsUsage
        let xboxJson = await fetch("https://user.auth.xboxlive.com/user/authenticate", {
            method: "post",
            body: JSON.stringify({
                Properties: {
                    AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com",
                    RpsTicket: "d=" + msTokenResp.accessToken
                }, RelyingParty: "http://auth.xboxlive.com", TokenType: "JWT"
            }),
            headers: { "content-type": "application/json" }
        })
            .then(checkFetchSuccess("xbox response not success"))
            .then(r => r.json());
        let xstsJson = await fetch("https://xsts.auth.xboxlive.com/xsts/authorize", {
            method: "post",
            body: JSON.stringify({
                Properties: { SandboxId: "RETAIL", UserTokens: [xboxJson.Token] },
                RelyingParty: "rp://api.minecraftservices.com/", TokenType: "JWT"
            }),
            headers: { "content-type": "application/json" }
        })
            .then(resp => {
            if (resp.status !== 401)
                return resp;
            return resp.json().then(errorData => {
                let error = errorData.XErr;
                switch (error) {
                    case 2148916233:
                        throw "Xbox account not found";
                    case 2148916235:
                        throw "Xbox Live not available in this country";
                    case 2148916238:
                        throw "Account is underage, add it to a family";
                }
                throw "xsts error code " + error;
            });
        })
            .then(checkFetchSuccess("xsts response not success"))
            .then(r => r.json());
        let mcJson = await fetch(getCorsProxy() + "https://api.minecraftservices.com/authentication/login_with_xbox", {
            method: "post",
            body: JSON.stringify({ identityToken: "XBL3.0 x=" + xstsJson.DisplayClaims.xui[0].uhs + ";" + xstsJson.Token }),
            headers: { "content-type": "application/json" }
        })
            .then(checkFetchSuccess("mc response not success"))
            .then(r => r.json());
        let jsonProfile = await fetch(getCorsProxy() + "https://api.minecraftservices.com/minecraft/profile", {
            method: "get",
            headers: { "content-type": "application/json", "authorization": "Bearer " + mcJson.access_token }
        })
            .then(profile => {
            if (profile.status === 404)
                throw "Minecraft profile not found";
            if (!profile.ok)
                throw "profile response not success " + profile.status;
            return profile.json();
        });
        this.accessToken = mcJson.access_token;
        this.name = jsonProfile.name;
        this.id = jsonProfile.id;
        saveRefreshAccounts();
    }
    async checkActive() {
        return fetch(getCorsProxy() + "https://api.minecraftservices.com/entitlements/mcstore", {
            method: "get",
            headers: { "authorization": "Bearer " + this.accessToken }
        }).then(data => data.ok);
    }
}
function findAccountByMcName(name) {
    return activeAccounts.find(it => it.name.toLowerCase() === name.toLowerCase());
}
function findAccountByMs(username) {
    return getActiveAccounts().find(it => it.msUser === username);
}
function addActiveAccount(acc) {
    activeAccounts.push(acc);
    saveRefreshAccounts();
}
function getLoginRequest() {
    return { scopes: ["XboxLive.signin"] };
}
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
        cacheLocation: "localStorage",
        storeAuthStateInCookie: false,
    }
};
// @ts-ignore
const myMSALObj = new msal.PublicClientApplication(msalConfig);
function loginMs() {
    let req = getLoginRequest();
    req.prompt = "select_account";
    myMSALObj.loginRedirect(req);
}
$(() => myMSALObj.handleRedirectPromise().then((resp) => {
    if (resp) {
        let found = findAccountByMs(resp.account.username);
        if (!found) {
            let accNew = new MicrosoftAccount("", "", "", resp.account.username);
            accNew.refresh()
                .then(() => addActiveAccount(accNew))
                .catch(e => addToast("Failed to get mc token", e));
        }
        else {
            found.refresh()
                .catch(e => addToast("Failed to refresh mc token", e));
        }
    }
}));
function getTokenPopup(username, request) {
    request.account = myMSALObj.getAccountByUsername(username);
    request.loginHint = username;
    return myMSALObj.acquireTokenSilent(request)
        .catch((e) => {
        console.warn("silent token acquisition fails.");
        // @ts-ignore
        if (e instanceof msal.InteractionRequiredAuthError) {
            return myMSALObj.acquireTokenPopup(request).catch((error) => console.error(error));
        }
        else {
            console.warn(e);
        }
    });
}
// Websocket
let wsUrl = getWsUrl();
let socket = null;
function defaultWs() {
    let url = new URL("ws", location.href);
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
    let serialized = localStorage.getItem("viaaas_tokens");
    let hTokens = serialized ? JSON.parse(serialized) : {};
    let tokens = getTokens();
    tokens.push(token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("viaaas_tokens", JSON.stringify(hTokens));
}
function removeToken(token) {
    let serialized = localStorage.getItem("viaaas_tokens");
    let hTokens = serialized ? JSON.parse(serialized) : {};
    let tokens = getTokens();
    tokens = tokens.filter(it => it !== token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("viaaas_tokens", JSON.stringify(hTokens));
}
function getTokens() {
    let serialized = localStorage.getItem("viaaas_tokens");
    let parsed = serialized ? JSON.parse(serialized) : {};
    return parsed[wsUrl] || [];
}
// Websocket
function listen(token) {
    socket === null || socket === void 0 ? void 0 : socket.send(JSON.stringify({ "action": "listen_login_requests", "token": token }));
}
function unlisten(id) {
    socket === null || socket === void 0 ? void 0 : socket.send(JSON.stringify({ "action": "unlisten_login_requests", "uuid": id }));
}
function confirmJoin(hash) {
    socket === null || socket === void 0 ? void 0 : socket.send(JSON.stringify({ action: "session_hash_response", session_hash: hash }));
}
function handleJoinRequest(parsed) {
    authNotification("Allow auth impersonation from VIAaaS instance?\nAccount: "
        + parsed.user + "\nServer Message: \n"
        + parsed.message.split(/[\r\n]+/).map((it) => "> " + it).join('\n'), () => {
        let account = findAccountByMcName(parsed.user);
        if (account) {
            account.joinGame(parsed.session_hash)
                .finally(() => confirmJoin(parsed.session_hash))
                .catch((e) => addToast("Couldn't contact session server", "Error: " + e));
        }
        else {
            confirmJoin(parsed.session_hash);
            addToast("Couldn't find account", "Couldn't find " + parsed.user + ", check Accounts tab");
        }
    }, () => confirmJoin(parsed.session_hash));
}
function onWsMsg(event) {
    let parsed = JSON.parse(event.data);
    switch (parsed.action) {
        case "ad_login_methods":
            listenVisible = true;
            renderActions();
            break;
        case "login_result":
            if (!parsed.success) {
                addToast("Couldn't verify Minecraft account", "VIAaaS returned failed response");
            }
            else {
                listen(parsed.token);
                saveToken(parsed.token);
            }
            break;
        case "listen_login_requests_result":
            if (parsed.success) {
                addListeningList(parsed.user, parsed.username, parsed.token);
            }
            else {
                removeToken(parsed.token);
            }
            break;
        case "session_hash_request":
            handleJoinRequest(parsed);
            break;
        case "parameters_request":
            handleParametersRequest(parsed);
            break;
    }
}
function handleParametersRequest(parsed) {
    let url = new URL("https://0.0.0.0");
    try {
        url = new URL("https://" + $("#connect_address").val());
    }
    catch (e) {
        console.log(e);
    }
    socket === null || socket === void 0 ? void 0 : socket.send(JSON.stringify({
        action: "parameters_response",
        callback: parsed.callback,
        version: $("#connect_version").val(),
        host: url.hostname,
        port: parseInt(url.port) || 25565,
        frontOnline: $("#connect_online").val(),
        backName: $("#connect_user").val() || undefined
    }));
}
function listenStoredTokens() {
    getTokens().forEach(listen);
}
function onWsConnect(e) {
    let msg = "connected";
    let socketHost = new URL(socket.url).host;
    if (socketHost != location.host)
        msg += ` to ${socketHost}`;
    setWsStatus(msg);
    resetHtml();
    listenStoredTokens();
}
function onWsError(e) {
    console.log(e);
    resetHtml();
}
function onWsClose(evt) {
    console.log(evt);
    setWsStatus(`disconnected: ${evt.code} ${evt.reason}`);
    socket = null;
    resetHtml();
    setTimeout(connect, 5000);
}
function connect() {
    setWsStatus("connecting...");
    try {
        socket = new WebSocket(wsUrl);
        socket.addEventListener("error", onWsError);
        socket.addEventListener("open", onWsConnect);
        socket.addEventListener("close", onWsClose);
        socket.addEventListener("message", onWsMsg);
    }
    catch (e) {
        console.log(e);
        setWsStatus(`error: ${e.toString()}`);
        setTimeout(connect, 5000);
    }
}
function sendSocket(msg) {
    if (!socket) {
        console.error("couldn't send msg, socket isn't set");
        return;
    }
    socket.send(msg);
}
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoicGFnZS5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbIi4uLy4uLy4uL3R5cGVzY3JpcHQvanMvcGFnZS50cyJdLCJuYW1lcyI6W10sIm1hcHBpbmdzIjoiO0FBQUEsa0NBQWtDO0FBQ2xDLHlDQUF5QztBQUV6QyxlQUFlO0FBQ2YsSUFBSSxTQUFTLEdBQUcsSUFBSSxlQUFlLEVBQUUsQ0FBQztBQUN0QyxNQUFNLENBQUMsUUFBUSxDQUFDLElBQUksQ0FBQyxTQUFTLENBQUMsQ0FBQyxDQUFDLENBQUMsS0FBSyxDQUFDLEdBQUcsQ0FBQztLQUN2QyxHQUFHLENBQUMsRUFBRSxDQUFDLEVBQUUsQ0FBQyxJQUFJLGVBQWUsQ0FBQyxFQUFFLENBQUM7S0FDN0IsT0FBTyxDQUFDLENBQUMsQ0FBQyxFQUFFLENBQUMsRUFBRSxFQUFFLENBQUMsU0FBUyxDQUFDLE1BQU0sQ0FBQyxDQUFDLEVBQUUsQ0FBQyxDQUFDLENBQUMsQ0FBQyxDQUFDO0FBQ3BELElBQUksWUFBWSxHQUFHLFNBQVMsQ0FBQyxHQUFHLENBQUMsVUFBVSxDQUFDLENBQUM7QUFDN0MsSUFBSSxRQUFRLEdBQUcsU0FBUyxDQUFDLEdBQUcsQ0FBQyxhQUFhLENBQUMsQ0FBQztBQUM1QyxJQUFJLFdBQVcsR0FBRyxTQUFTLENBQUMsR0FBRyxDQUFDLGdCQUFnQixDQUFDLENBQUM7QUFFbEQsQ0FBQyxDQUFDLEdBQUcsRUFBRTs7SUFDSCxJQUFJLFdBQVcsS0FBSyxPQUFPLEVBQUU7UUFDekIsUUFBUSxDQUFDLHlDQUF5QyxFQUFFLE1BQUEsU0FBUyxDQUFDLEdBQUcsQ0FBQyxZQUFZLENBQUMsbUNBQUksRUFBRSxDQUFDLENBQUM7S0FDMUY7SUFDRCxJQUFJLFFBQVEsSUFBSSxJQUFJLEVBQUU7UUFDbEIsT0FBTyxDQUFDLFlBQVksQ0FBQyxJQUFJLEVBQUUsRUFBRSxFQUFFLEdBQUcsQ0FBQyxDQUFDO0tBQ3ZDO0FBQ0wsQ0FBQyxDQUFDLENBQUM7QUFFSCxPQUFPO0FBQ1AsSUFBSSxnQkFBZ0Is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