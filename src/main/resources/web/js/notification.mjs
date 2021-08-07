// Notification
let notificationCallbacks = {};
$(() => {
    new BroadcastChannel("viaaas-notification")
        .addEventListener("message", handleSWMsg);
})

function handleSWMsg(event) {
    console.log("sw msg: " + event);
    let data = event.data;
    let callback = notificationCallbacks[data.tag];
    delete notificationCallbacks[data.tag];
    if (callback == null) return;
    callback(data.action);
}

export function authNotification(msg, yes, no) {
    if (!navigator.serviceWorker || Notification.permission !== "granted") {
        if (confirm(msg)) yes(); else no();
        return;
    }
    let tag = uuid.v4();
    navigator.serviceWorker.ready.then(r => {
        r.showNotification("Click to allow auth impersionation", {
            body: msg,
            tag: tag,
            vibrate: [200, 10, 100, 200, 100, 10, 100, 10, 200],
            actions: [
                {action: "reject", title: "Reject"},
                {action: "confirm", title: "Confirm"}
            ]
        });
        notificationCallbacks[tag] = action => {
            if (action === "reject") {
                no();
            } else if (!action || action === "confirm") {
                yes();
            }
        };
        setTimeout(() => {
            delete notificationCallbacks[tag]
        }, 30 * 1000);
    });
}
