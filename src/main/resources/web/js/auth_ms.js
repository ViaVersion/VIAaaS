// https://docs.microsoft.com/en-us/azure/active-directory/develop/tutorial-v2-javascript-auth-code

const azureClientId = "a370fff9-7648-4dbf-b96e-2b4f8d539ac2";
const whitelistedOrigin = ["https://localhost:25543", "https://via-login.geyserconnect.net", "https://via.re.yt.nom.br", "https://viaaas.noxt.cf"];
let redirectUrl = "https://viaversion.github.io/VIAaaS/src/main/resources/web/";
if (whitelistedOrigin.includes(location.origin)) {
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

const loginRequest = {
    scopes: ["XboxLive.signin"]
};

function loginMs() {
    myMSALObj.loginRedirect(loginRequest);
}

$(() => myMSALObj.handleRedirectPromise().then((resp) => {
    if (resp) {
        refreshTokenMs(resp.account.username).catch(e => addToast("Failed to get token", e));
        refreshAccountList();
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

function logoutMs(username) {
    let mcAcc = findAccountByMs(username) || {};
    removeMcAccount(mcAcc.id);

    const logoutRequest = {
        account: myMSALObj.getAccountByUsername(username)
    };

    myMSALObj.logout(logoutRequest);
}
