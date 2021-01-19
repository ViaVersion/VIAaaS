// https://docs.microsoft.com/en-us/azure/active-directory/develop/tutorial-v2-javascript-auth-code

const redirectUrl = location.origin == "https://localhost:25543" ?
"https://localhost:25543/auth.html" : "https://viaversion.github.io/VIAaaS/src/main/resources/web/auth.html";

// Config object to be passed to Msal on creation
const msalConfig = {
    auth: {
        clientId: "a370fff9-7648-4dbf-b96e-2b4f8d539ac2",
        authority: "https://login.microsoftonline.com/consumers/",
        redirectUri: redirectUrl,
    },
    cache: {
        cacheLocation: "sessionStorage", // This configures where your cache will be stored
        storeAuthStateInCookie: false, // Set this to "true" if you are having issues on IE11 or Edge
    }
};

const myMSALObj = new msal.PublicClientApplication(msalConfig);

// Add scopes for the id token to be used at Microsoft identity platform endpoints.
const loginRequest = {
    scopes: ["XboxLive.signin"]
};

function loginMs() {
    myMSALObj.loginRedirect(loginRequest);
}

function handleResponse(response) {
    const currentAccounts = myMSALObj.getAllAccounts();
    refreshAccountList();
}

$(() => {
myMSALObj.handleRedirectPromise(handleResponse);
});

function getMcToken(username) {
    return getTokenPopup(username, loginRequest)
        .then((response) => {
            // this supports CORS
            return fetch("https://user.auth.xboxlive.com/user/authenticate", {method: "post",
                data: JSON.stringify({"Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com",
                    "RpsTicket": "d=" + response.accessToken}, "RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT"}),
                    headers: {"content-type": "application/json"}});
        }).then(xboxResponse => {
            if (xboxResponse != 200) throw "xbox response not 200: " + xboxResponse;
            // We need CORS proxy
            return fetch(getCorsProxy() + "https://xsts.auth.xboxlive.com/xsts/authorize", {method: "post",
                   data: JSON.stringify({"Properties": {"SandboxId": "RETAIL", "UserTokens": [xboxResponse.json().Token]},
                       "RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT"}),
                   headers: {"content-type": "application/json"}});
        }).then(xstsResponse => {
            // Need CORS proxy here too
            return fetch(getCorsProxy() + "https://api.minecraftservices.com/authentication/login_with_xbox", {
                data: JSON.stringify({"identityToken": "XBL3.0 x=" + xstsResponse.json().DisplayClaims.xui.uhs + ";"
                    + xstsResponse.json().Token}), headers: {"content-type": "application/json"}});
        }).then(mcResponse => {
            console.log(mcResponse); // finally!!!.. todo
            return mcResponse;
        }).catch(error => {
            console.log(error);
        });
}

function getTokenPopup(username, request) {
    /**
     * See here for more info on account retrieval:
     * https://github.com/AzureAD/microsoft-authentication-library-for-js/blob/dev/lib/msal-common/docs/Accounts.md
     */
    request.account = myMSALObj.getAccountByUsername(username);
    return myMSALObj.acquireTokenSilent(request).catch(error => {
        console.warn("silent token acquisition fails. acquiring token using redirect");
        if (error instanceof msal.InteractionRequiredAuthError) {
            // fallback to interaction when silent call fails
            return myMSALObj.acquireTokenPopup(request).then(tokenResponse => {
                console.log(tokenResponse);

                return tokenResponse;
            }).catch(error => {
                console.error(error);
            });
        } else {
            console.warn(error);
        }
    });
}

function signOut(username) {
    const logoutRequest = {
        account: myMSALObj.getAccountByUsername(username)
    };

    myMSALObj.logout(logoutRequest);
}
