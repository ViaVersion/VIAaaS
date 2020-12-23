$(() => {
    let urlParams = new URLSearchParams();
    window.location.hash.substr(1).split("?").map(it => new URLSearchParams(it).forEach((a, b) => urlParams.append(b, a)));
    var username = urlParams.get("username");
    var mcauth_code = urlParams.get("mcauth_code");
    if (urlParams.get("mcauth_success") == "false") {
        alert("Couldn't authenticate with Minecraft.ID: " + urlParams.get("mcauth_msg"));
    }

    var wsUrl = window.location.host == "viaversion.github.io" ? prompt("VIAaaS instance WS URL") : "wss://" + window.location.host + "/ws";

    var socket = null;
    var connectionStatus = document.getElementById("connection_status");
    var content = document.getElementById("content");
    var acounts = document.getElementById("accounts");

    $("#cors-proxy").val(localStorage.getItem("cors-proxy"));
    $("#cors-proxy").on("change", () => localStorage.setItem('cors-proxy', $("#cors-proxy").val()));
    $("#login_submit_mc").on("click", loginMc);

    function loginMc() {
        var clientToken = uuid.v4();
        $.ajax({type: "post",
            url: localStorage.getItem("cors-proxy") + "https://authserver.mojang.com/authenticate",
            data: JSON.stringify({
                agent: {name: "Minecraft", version: 1},
                username: $("#email").val(),
                password: $("#password").val(),
                clientToken: clientToken,
            }),
            contentType: "application/json",
            dataType: "json"
        }).done((data) => {
            storeMcAccount(data.accessToken, data.clientToken, data.selectedProfile.name, data.selectedProfile.id);
        }).fail(() => alert("Failed to login"));
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
            $.ajax({type: "post",
                url: localStorage.getItem("cors-proxy") + "https://authserver.mojang.com/invalidate",
                data: JSON.stringify({
                    accessToken: it.accessToken,
                    clientToken: it.clientToken
                }),
                contentType: "application/json",
                dataType: "json"
            }).done((data) => {
                removeMcAccount(id);
            }).fail(() => {
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
        head.src = "https://crafatar.com/avatars/" + id + "?overlay";
        p.append(head);
        p.append(n);
        p.append(remove);
        accounts.appendChild(p);
    }

    function refreshAccountList() {
        accounts.innerHTML = "";
        getMcAccounts().forEach(it => addMcAccountToList(it.id, it.name));
    }

    function refreshAccountIfNeeded(it, doneCallback, failCallback) {
        $.ajax({type: "post",
            url: localStorage.getItem("cors-proxy") + "https://authserver.mojang.com/validate",
            data: JSON.stringify({
                accessToken: it.accessToken,
                clientToken: it.clientToken
            }),
            contentType: "application/json",
            dataType: "json"
        })
        .done(() => doneCallback(it))
        .fail(() => {
            // Needs refresh
            console.log("refreshing " + it.id);
            $.ajax({type: "post",
                url: localStorage.getItem("cors-proxy") + "https://authserver.mojang.com/refresh",
                data: JSON.stringify({
                    accessToken: it.accessToken,
                    clientToken: it.clientToken
                }),
                contentType: "application/json",
                dataType: "json"
            }).done((data) => {
                console.log("refreshed " + data.selectedProfile.id);
                removeMcAccount(data.selectedProfile.id);
                doneCallback(storeMcAccount(data.accessToken, data.clientToken, data.selectedProfile.name, data.selectedProfile.id));
            }).fail(() => {
                if (confirm("failed to refresh token! remove account?")) {
                    removeMcAccount(it.id);
                }
                failCallback();
            });
        });
    }

    refreshAccountList();

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

        socket.onmessage = event => {
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
                        refreshAccountIfNeeded(account, (data) => {
                            $.ajax({
                                type: "post",
                                url: localStorage.getItem("cors-proxy") + "https://sessionserver.mojang.com/session/minecraft/join",
                                data: JSON.stringify({
                                    accessToken: data.accessToken,
                                    selectedProfile: data.id,
                                    serverId: parsed.session_hash
                                }),
                                contentType: "application/json",
                                dataType: "json"
                            }).done((data) => {
                                confirmJoin(parsed.session_hash);
                            }).fail((e) => {
                                console.log(e);
                                confirmJoin(parsed.session_hash);
                                alert("Failed to contact session server!");
                            });
                        }, () => {
                            confirmJoin(parsed.session_hash);
                            alert("Couldn't refresh " + parsed.user + " account in browser.");
                        });
                    } else {
                        alert("Couldn't find " + parsed.user + " account in browser.");
                        confirmJoin(parsed.session_hash);
                    }
                } else if (confirm("Continue without authentication (works on LAN worlds)?")) {
                    confirmJoin(parsed.session_hash);
                }
            }
        };
    }

    connect();
});
