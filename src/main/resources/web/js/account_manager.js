import {getCorsProxy} from "./cors_proxy.js";
import {checkFetchSuccess, filterNot, isSuccess} from "./util.js";
import {addToast, refreshAccountList} from "./page.js";

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

export function getActiveAccounts() {
    return activeAccounts;
}

export function getMicrosoftUsers() {
    return (myMSALObj.getAllAccounts() || []).map(it => it.username);
}

export class McAccount {
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

export class MojangAccount extends McAccount {
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

export class MicrosoftAccount extends McAccount {
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
            .then(response => {
                // this supports CORS
                return fetch("https://user.auth.xboxlive.com/user/authenticate", {
                    method: "post",
                    body: JSON.stringify({
                        Properties: {
                            AuthMethod: "RPS", SiteName: "user.auth.xboxlive.com",
                            RpsTicket: "d=" + response.accessToken
                        }, RelyingParty: "http://auth.xboxlive.com", TokenType: "JWT"
                    }),
                    headers: {"content-type": "application/json"}
                }).then(checkFetchSuccess("xbox response not success"))
                    .then(r => r.json());
            }).then(json => {
                return fetch("https://xsts.auth.xboxlive.com/xsts/authorize", {
                    method: "post",
                    body: JSON.stringify({
                        Properties: {SandboxId: "RETAIL", UserTokens: [json.Token]},
                        RelyingParty: "rp://api.minecraftservices.com/", TokenType: "JWT"
                    }),
                    headers: {"content-type": "application/json"}
                }).then(checkFetchSuccess("xsts response not success"))
                    .then(r => r.json());
            }).then(json => {
                return fetch(getCorsProxy() + "https://api.minecraftservices.com/authentication/login_with_xbox", {
                    method: "post",
                    body: JSON.stringify({identityToken: "XBL3.0 x=" + json.DisplayClaims.xui[0].uhs + ";" + json.Token}),
                    headers: {"content-type": "application/json"}
                }).then(checkFetchSuccess("mc response not success"))
                    .then(r => r.json());
            }).then(json => {
                return fetch(getCorsProxy() + "https://api.minecraftservices.com/minecraft/profile", {
                    method: "get",
                    headers: {"content-type": "application/json", "authorization": "Bearer " + json.access_token}
                }).then(profile => {
                    if (profile.status === 404) return {id: "MHF_Exclamation", name: "[DEMO]", access_token: ""};
                    if (!isSuccess(profile.status)) throw "profile response not success";
                    return profile.json();
                }).then(jsonProfile => {
                    this.accessToken = json.access_token;
                    this.name = jsonProfile.name;
                    this.id = jsonProfile.id;
                    saveRefreshAccounts();
                });
            });
    }
}

export function findAccountByMcName(name) {
    return activeAccounts.find(it => it.name.toLowerCase() === name.toLowerCase());
}

export function findAccountByMs(username) {
    return getActiveAccounts().find(it => it.msUser === username);
}

function addActiveAccount(acc) {
    activeAccounts.push(acc)
    saveRefreshAccounts()
}

export function loginMc(user, pass) {
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

export function loginMs() {
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
