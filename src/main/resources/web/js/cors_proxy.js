import {refreshCorsStatus} from "./page.js";

function defaultCors() {
    return "https://crp123-cors.herokuapp.com/";
}

export function getCorsProxy() {
    return localStorage.getItem("viaaas_cors_proxy") || defaultCors();
}

export function setCorsProxy(url) {
    localStorage.setItem("viaaas_cors_proxy", url);
    refreshCorsStatus();
}
