function isSuccess(status) {
    return status >= 200 && status < 300;
}

function checkFetchSuccess(msg) {
    return r => {
        if (!isSuccess(r.status)) throw r.status + " " + msg;
        return r;
    };
}

function icanhazip(cors) {
    return fetch((cors ? getCorsProxy() : "") + "https://ipv4.icanhazip.com")
        .then(checkFetchSuccess("code"))
        .then(r => r.text())
        .then(it => it.trim());
}

function icanhazepoch() {
    return fetch("https://icanhazepoch.com")
        .then(checkFetchSuccess("code"))
        .then(r => r.text())
        .then(it => parseInt(it.trim()))
}