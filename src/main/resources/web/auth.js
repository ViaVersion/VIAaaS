let urlParams = new URLSearchParams();
window.location.hash.substr(1).split("?").map(it => new URLSearchParams(it).forEach((a, b) => urlParams.append(b, a)));
var username = urlParams.get("username");
var mcauth_code = urlParams.get("mcauth_code");
if (urlParams.get("mcauth_success") == "false") {
    alert("Couldn't authenticate with Minecraft.ID: " + urlParams.get("mcauth_msg"));
}

function askWsUrl() {
    let url = localStorage.getItem("ws-url") || "wss://localhost:25543/ws";
    url = prompt("VIAaaS instance websocket", url) || url;
    localStorage.setItem("ws-url", url);
    return url;
}

var wsUrl = window.location.host == "viaversion.github.io" ? askWsUrl() : "wss://" + window.location.host + "/ws";

var socket = null;
var connectionStatus = document.getElementById("connection_status");
var content = document.getElementById("content");
var acounts = document.getElementById("accounts");

function getCorsProxy() {
    return localStorage.getItem("cors-proxy") || "http://localhost:8080/";
}

function loginMc() {
    var clientToken = uuid.v4();
    fetch(getCorsProxy() + "https://authserver.mojang.com/authenticate", {method: "post",
        body: JSON.stringify({
            agent: {name: "Minecraft", version: 1},
            username: $("#email").val(),
            password: $("#password").val(),
            clientToken: clientToken,
        }),
        headers: {"content-type": "application/json"}
    }).then((data) => {
        if (data.status != 200) throw "not 200";
        storeMcAccount(data.json().accessToken, data.json().clientToken, data.json().selectedProfile.name, data.json().selectedProfile.id);
    }).catch(() => alert("Failed to login"));
    $("#email").val("");
    $("#password").val("");
}

function storeMcAccount(accessToken, clientToken, name, id) {
    let accounts = JSON.parse(localStorage.getItem("mc_accounts")) || [];
    let account = {accessToken: accessToken, clientToken: clientToken, name: name, id: id};
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

function logout(id) {
    getMcAccounts().filter(it => it.id == id).forEach(it => {
        fetch(getCorsProxy() + "https://authserver.mojang.com/invalidate", {method: "post",
            body: JSON.stringify({
                accessToken: it.accessToken,
                clientToken: it.clientToken
            }),
            headers: {"content-type": "application/json"},
        }).then((data) => {
            if (data.status != 200) throw "not 200";
            removeMcAccount(id);
        }).catch(() => {
            if (confirm("failed to invalidate token! remove account?")) {
                removeMcAccount(id);
            }
        });
    });
}

function addMcAccountToList(id, name) {
    let p = document.createElement("p");
    let head = document.createElement("img");
    let n = document.createElement("span");
    let remove = document.createElement("a");
    n.innerText = " " + name + " ";
    remove.innerText = "Remove";
    remove.href = "#";
    remove.onclick = () => {
        logout(id);
    };
    head.className = "account_head";
    head.alt = name + "'s head";
    head.src = "https://crafthead.net/helm/" + id;
    p.append(head);
    p.append(n);
    p.append(remove);
    accounts.appendChild(p);
}

function addMsAccountToList(id, name, msUser) {
    let p = document.createElement("p");
    let head = document.createElement("img");
    let n = document.createElement("span");
    let remove = document.createElement("a");
    n.innerText = " " + name + "(MS: " + msUser + ") ";
    remove.innerText = "Logout";
    remove.href = "#";
    remove.onclick = () => {
        signOut(msUser);
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
    getMcAccounts().forEach(it => addMcAccountToList(it.id, it.name));
    (myMSALObj.getAllAccounts() || []).forEach(it => addMsAccountToList("TODO", "TODO", it.username))
}

function refreshAccountIfNeeded(it) {
    return fetch(getCorsProxy() + "https://authserver.mojang.com/validate", {method: "post",
        body: JSON.stringify({
            accessToken: it.accessToken,
            clientToken: it.clientToken
        }),
        headers: {"content-type": "application/json"}
    }).then((data) => {
        if (data.status != 200) {
            console.log("refreshing " + it.id);
            return fetch(getCorsProxy() + "https://authserver.mojang.com/refresh", {
                method: "post",
                body: JSON.stringify({
                    accessToken: it.accessToken,
                    clientToken: it.clientToken
                }),
                headers: {"content-type": "application/json"},
            }).then(data => {
                if (data.status != 200) throw "not 200 refresh";
                console.log("refreshed " + data.selectedProfile.id);
                removeMcAccount(data.selectedProfile.id);
                const json = data.json();
                return storeMcAccount(json.accessToken, json.clientToken, json.selectedProfile.name, json.selectedProfile.id);
            });
        }
        return it;
    }).catch(() => {
        alert("failed to refresh token!");
    });
}

function listen(token) {
    socket.send(JSON.stringify({"action": "listen_login_requests", "token": token}));
}

function confirmJoin(hash) {
    socket.send(JSON.stringify({action: "session_hash_response", session_hash: hash}));
}

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

function showListenAccount() {
    if (username != null && mcauth_code != null) {
        let p = document.createElement("p");
        let add = document.createElement("a");
        p.appendChild(add);
        add.innerText = "Listen to " + username;
        add.href = "#";
        add.onclick = () => {
            socket.send(JSON.stringify({
                "action": "minecraft_id_login",
                "username": username,
                "code": mcauth_code}));
        };
        content.appendChild(p);
    }
    let p = document.createElement("p");
    let link = document.createElement("a");
    p.appendChild(link);
    link.innerText = "Listen to username in VIAaaS instance";
    link.href = "#";
    link.onclick = () => {
        let user = prompt("Username (Minecraft.ID is case-sensitive): ", "");
        let callbackUrl = new URL(location.origin + location.pathname + "#username=" + encodeURIComponent(user));
        location = "https://api.minecraft.id/gateway/start/" + encodeURIComponent(user) + "?callback=" + encodeURIComponent(callbackUrl);
    };
    content.appendChild(p);
}

function onSocketMsg(event) {
    console.log(event.data.toString());
    let parsed = JSON.parse(event.data);
    if (parsed.action == "ad_minecraft_id_login") {
        showListenAccount();
    } else if (parsed.action == "minecraft_id_result") {
        if (!parsed.success) {
            alert("VIAaaS instance couldn't verify account via Minecraft.ID");
        } else {
            listen(parsed.token);
            saveToken(parsed.token);
        }
    } else if (parsed.action == "listen_login_requests_result") {
        if (parsed.success) {
            let msg = document.createElement("p");
            msg.innerText = "Listening to login: " + parsed.user;
            content.appendChild(msg);
        } else {
            removeToken(parsed.token);
        }
    } else if (parsed.action == "session_hash_request") {
        if (confirm("Allow auth impersonation from VIAaaS instance? info: " + JSON.stringify(parsed))) {
            let account = getMcAccounts().reverse().find(it => it.name.toLowerCase() == parsed.user.toLowerCase());
            if (account) {
                refreshAccountIfNeeded(account).then((data) => {
                    return fetch(getCorsProxy() + "https://sessionserver.mojang.com/session/minecraft/join", {
                        method: "post",
                        body: JSON.stringify({
                            accessToken: data.accessToken,
                            selectedProfile: data.id,
                            serverId: parsed.session_hash
                        }),
                        headers: {"content-type": "application/json"}
                    });
                }).then((data) => {
                    if (data.status != 200) throw "not 200";
                }).finally(() => confirmJoin(parsed.session_hash))
                .catch((e) => {
                    confirmJoin(parsed.session_hash);
                    alert("Couldn't contact session server for " + parsed.user + " account in browser.");
                });
            } else {
                alert("Couldn't find " + parsed.user + " account in browser.");
                confirmJoin(parsed.session_hash);
            }
        } else if (confirm("Continue without authentication (works on LAN worlds)?")) {
            confirmJoin(parsed.session_hash);
        }
    }
}

function connect() {
    connectionStatus.innerText = "connecting...";
    socket = new WebSocket(wsUrl);

    socket.onerror = e => {
        console.log(e);
        connectionStatus.innerText = "socket error";
        content.innerHTML = "";
    };

    socket.onopen = () => {
        connectionStatus.innerText = "connected";
        content.innerHTML = "";

        getTokens().forEach(listen);
    };

    socket.onclose = evt => {
        connectionStatus.innerText = "disconnected with close code " + evt.code + " and reason: " + evt.reason;
        content.innerHTML = "";
        setTimeout(connect, 5000);
    };

    socket.onmessage = onSocketMsg;
}


$(() => {
    $("#cors-proxy").on("change", () => localStorage.setItem('cors-proxy', $("#cors-proxy").val()));
    $("#cors-proxy").val(getCorsProxy());
    $("#login_submit_mc").on("click", loginMc);
    $("#login_submit_ms").on("click", loginMs);

    refreshAccountList();

    connect();
});
