import {getCorsProxy} from "./cors_proxy.js";

export function isSuccess(status) {
    return status >= 200 && status < 300;
}

export function checkFetchSuccess(msg) {
    return r => {
        if (!isSuccess(r.status)) throw r.status + " " + msg;
        return r;
    };
}

export function icanhazip(cors) {
    return fetch((cors ? getCorsProxy() : "") + "https://ipv4.icanhazip.com")
        .then(checkFetchSuccess("code"))
        .then(r => r.text())
        .then(it => it.trim());
}

export function icanhazepoch() {
    return fetch("https://icanhazepoch.com")
        .then(checkFetchSuccess("code"))
        .then(r => r.text())
        .then(it => parseInt(it.trim()))
}

export function filterNot(array, item) {
    return array.filter(it => it !== item);
}