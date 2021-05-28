// https://stackoverflow.com/questions/42127148/service-worker-communicate-to-clients
let viac = new BroadcastChannel("viaaas-notification");
self.addEventListener("notificationclick", event => {
  event.preventDefault();
  event.notification.close();
  viac.postMessage({tag: event.notification.tag, action: event.action});
});

addEventListener("fetch", e => {
  // chrome please show "add to home screen"
});
