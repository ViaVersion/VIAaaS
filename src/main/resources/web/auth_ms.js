// https://docs.microsoft.com/en-us/azure/active-directory/develop/tutorial-v2-javascript-auth-code

const redirectUrl = location.origin == "https://localhost:25543" ?
"https://localhost:25543/" : "https://viaversion.github.io/VIAaaS/src/main/resources/web/";

const msalConfig = {
    auth: {
        clientId: "a370fff9-7648-4dbf-b96e-2b4f8d539ac2",
        authority: "https://login.microsoftonline.com/consumers/",
        redirectUri: redirectUrl,
    },
    cache: {
        cacheLocation: "sessionStorage",
        storeAuthStateInCookie: false,
    }
};

const myMSALObj = new msal.PublicClientApplication(msalConfig);

const loginRequest = {
    scopes: ["XboxLive.signin"]
};

function loginMs() {
    myMSALObj.loginRedirect(loginRequest);
}

$(() => myMSALObj.handleRedirectPromise().then((resp) => {
    if (resp) {
        refreshTokenMs(resp.account.username).catch(e => alert("failed to get mc token: " + e));
        refreshAccountList();
    }
}));

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

function logoutMs(username) {
    let mcAcc = findAccountByMs(username) || {};
    removeMcAccount(mcAcc.id);

    const logoutRequest = {
        account: myMSALObj.getAccountByUsername(username)
    };

    myMSALObj.logout(logoutRequest);
}
