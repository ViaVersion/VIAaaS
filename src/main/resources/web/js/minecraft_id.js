// Minecraft.id
var mcIdUsername = null;
var mcauth_code = null;
var mcauth_success = null;

$(() => {
    let urlParams = new URLSearchParams();
    window.location.hash.substr(1).split("?").map(it => new URLSearchParams(it).forEach((a, b) => urlParams.append(b, a)));
    mcIdUsername = urlParams.get("username");
    mcauth_code = urlParams.get("mcauth_code");
    mcauth_success = urlParams.get("mcauth_success");
    if (mcauth_success == "false") {
        addToast("Couldn't authenticate with Minecraft.ID", urlParams.get("mcauth_msg"));
    }
    if (mcauth_code != null) {
        history.replaceState(null, null, "#");
        renderActions();
    }
});