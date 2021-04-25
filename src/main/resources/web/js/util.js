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

function sha512(s) {
    const shaObj = new jsSHA("SHA-512", "TEXT", { encoding: "UTF8" });
    shaObj.update(s);
    return shaObj.getHash("HEX");
}
