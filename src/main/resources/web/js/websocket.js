import {authNotification} from "./notification.js";
import {checkFetchSuccess} from "./util.js";
import {findAccountByMcName} from "./account_manager.js";
import {addListeningList, addToast, renderActions, resetHtml, setListenVisible, setWsStatus} from "./page.js";

let wsUrl = getWsUrl();
let socket = null;

// WS url
function defaultWs() {
    let url = new URL("ws", new URL(location));
    url.protocol = "wss";
    return window.location.host.endsWith("github.io") || !window.location.protocol.startsWith("http")
        ? "wss://localhost:25543/ws" : url.toString();
}

export function getWsUrl() {
    return localStorage.getItem("viaaas_ws_url") || defaultWs();
}

export function setWsUrl(url) {
    localStorage.setItem("viaaas_ws_url", url);
    location.reload();
}

// Tokens
export function saveToken(token) {
    let hTokens = JSON.parse(localStorage.getItem("viaaas_tokens")) || {};
    let tokens = getTokens();
    tokens.push(token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("viaaas_tokens", JSON.stringify(hTokens));
}

export function removeToken(token) {
    let hTokens = JSON.parse(localStorage.getItem("viaaas_tokens")) || {};
    let tokens = getTokens();
    tokens = tokens.filter(it => it !== token);
    hTokens[wsUrl] = tokens;
    localStorage.setItem("viaaas_tokens", JSON.stringify(hTokens));
}

export function getTokens() {
    return (JSON.parse(localStorage.getItem("viaaas_tokens")) || {})[wsUrl] || [];
}

// Websocket
export function listen(token) {
    socket.send(JSON.stringify({"action": "listen_login_requests", "token": token}));
}

export function unlisten(id) {
    socket.send(JSON.stringify({"action": "unlisten_login_requests", "uuid": id}));
}

export function confirmJoin(hash) {
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

export function listenStoredTokens() {
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

export function connect() {
    setWsStatus("connecting...");
    socket = new WebSocket(wsUrl);

    socket.onerror = onWsError;
    socket.onopen = onConnect;
    socket.onclose = onDisconnect
    socket.onmessage = onSocketMsg;
}

export function sendSocket(msg) {
    if (!socket) {
        console.error("couldn't send msg, socket isn't set");
        return
    }
    socket.send(msg);
}