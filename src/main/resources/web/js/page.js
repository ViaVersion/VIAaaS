"use strict";
/// <reference path='config.ts' />
// Note that some APIs only work on HTTPS
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
    $("#address_info_form").on("input", () => generateAddress());
    $("#generated_address").on("click", () => copyGeneratedAddress());
    $("#listen_online").on("change", () => updateTempCodeVisibility());
    $("#link_address").text("link." + instance_suffix_input.value);
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
function submittedListen() {
    let user = $("#listen_username").val();
    if (!user)
        return;
    if ($("#listen_online")[0].checked) {
        sendSocket(JSON.stringify({
            action: "temp_code_login",
            username: user,
            code: $("#listen_code").val()
        }));
        // todo
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
function renderActions() {
    $("#en_notifications").hide();
    $("#listen_open").hide();
    $("#send_token_open").hide();
    if (Notification.permission === "default") {
        $("#en_notifications").show();
    }
    if (listenVisible) {
        $("#listen_open").show();
        $("#send_token_open").show();
    }
}
function updateTempCodeVisibility() {
    let tmpCode = $("#listen_code");
    if ($("#listen_online")[0].checked) {
        tmpCode.prop("disabled", false);
    }
    else {
        tmpCode.prop("disabled", true);
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
        addToast("Allow auth?", msg, yes, no);
        return;
    }
    // @ts-ignore
    let tag = uuid.v4();
    navigator.serviceWorker.ready.then(r => {
        let options = {
            body: msg,
            tag: tag,
            vibrate: [200, 10, 100, 200, 100, 10, 100, 10, 200],
            actions: [
                { action: "reject", title: "Reject" },
                { action: "confirm", title: "Confirm" }
            ]
        };
        r.showNotification("Click to allow auth", options).then(() => {
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
let redirectUrl = location.origin + location.pathname;
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
    authNotification("Allow auth from VIAaaS instance?\nAccount: "
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
