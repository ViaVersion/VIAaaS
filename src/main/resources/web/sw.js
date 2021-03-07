self.addEventListener("notificationclick", event => {
  console.log("On notification click: ", event);
  event.preventDefault();
  event.notification.close();
  self.clients.matchAll({type: "window"}).then(it => it.forEach(c => c.postMessage({tag: event.notification.tag, action: event.action})));
});
