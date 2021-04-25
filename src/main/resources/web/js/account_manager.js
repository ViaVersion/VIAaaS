// Account storage
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

// Generic
function getMcUserToken(account) {
    return validateToken(account.accessToken, account.clientToken || undefined).then(data => {
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

function validateToken(accessToken, clientToken) {
    return fetch(getCorsProxy() + "https://authserver.mojang.com/validate", {
        method: "post",
        body: JSON.stringify({
            accessToken: accessToken,
            clientToken: clientToken
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

// Microsoft auth
function refreshTokenMs(username) {
    return getTokenPopup(username, loginRequest)
        .then(response => {
            // this supports CORS
            return fetch("https://user.auth.xboxlive.com/user/authenticate", {method: "post",
                body: JSON.stringify({Properties: {AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com",
                        RpsTicket: "d=" + response.accessToken}, RelyingParty: "http://auth.xboxlive.com", TokenType: "JWT"}),
                    headers: {"content-type": "application/json"}})
                .then(checkFetchSuccess("xbox response not success"))
                .then(r => r.json());
        }).then(json => {
            return fetch("https://xsts.auth.xboxlive.com/xsts/authorize", {method: "post",
                   body: JSON.stringify({Properties: {SandboxId: "RETAIL", UserTokens: [json.Token]},
                       RelyingParty: "rp://api.minecraftservices.com/", TokenType: "JWT"}),
                   headers: {"content-type": "application/json"}})
                .then(checkFetchSuccess("xsts response not success"))
                .then(r => r.json());
        }).then(json => {
            return fetch(getCorsProxy() + "https://api.minecraftservices.com/authentication/login_with_xbox", {method: "post",
                    body: JSON.stringify({identityToken: "XBL3.0 x=" + json.DisplayClaims.xui[0].uhs + ";" + json.Token}),
                    headers: {"content-type": "application/json"}})
                .then(checkFetchSuccess("mc response not success"))
                .then(r => r.json());
        }).then(json => {
            return fetch(getCorsProxy() + "https://api.minecraftservices.com/minecraft/profile", {
                method: "get", headers: {"content-type": "application/json", "authorization": "Bearer " + json.access_token}}).then(profile => {
                if (profile.status == 404) return {id: "MHF_Exclamation", name: "[DEMO]"};
                if (!isSuccess(profile.status)) throw "profile response not success";
                return profile.json();
            }).then(jsonProfile => {
                removeMcAccount(jsonProfile.id);
                return storeMcAccount(json.access_token, null, jsonProfile.name, jsonProfile.id, username);
            });
        });
}

function isMojang(it) {
    return !!it.clientToken;
}

function isNotMojang(it) {
    return !isMojang(it);
}