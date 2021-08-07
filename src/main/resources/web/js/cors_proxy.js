function defaultCors() {
    return "https://crp123-cors.herokuapp.com/";
}
function getCorsProxy() {
    return localStorage.getItem("viaaas_cors_proxy") || defaultCors();
}
function setCorsProxy(url) {
    localStorage.setItem("viaaas_cors_proxy", url);
    refreshCorsStatus();
}
