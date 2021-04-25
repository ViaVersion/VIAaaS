var wsUrl = getWsUrl();
var socket = null;

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
function setWsUrl(url) {
    localStorage.setItem("ws-url", url);
    location.reload();
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