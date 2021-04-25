function defaultCors() {
    return "https://crp123-cors.herokuapp.com/";
}
function getCorsProxy() {
    return localStorage.getItem("cors-proxy") || defaultCors();
}
function setCorsProxy(url) {
    localStorage.setItem("cors-proxy", url);
    refreshCorsStatus();
}