VIAaaS
---
How to use: server.example.com._p25565._v1_12_2._uBACKUSERNAME.viaaas.example.com (similar to tor to web proxies)
Default WS URL: wss://localhost:25543/ws

- TODO: _o option for disabling online mode only in front end, protocol auto detection

- Connection to private IP addresses are currently blocked

- VIAaaS auth page is designed for storing accounts in the browser local storage.
 It requires a CORS Proxy for calling Mojang APIs, which may make Mojang see that
 as suspicious and reset/block your account password.

- There are some information about Mojang password resetting:
  https://github.com/GeyserMC/Geyser/wiki/Common-Issues#mojang-resetting-account-credentials and
  https://mobile.twitter.com/MojangSupport/status/863697596350517248

- VIAaaS may have security vulnerabilities, make sure to block the ports in firewall and take care of browser local storage.

Download: https://github.com/ViaVersion/VIAaaS/actions (needs to be logged into GitHub)

Requires Java 11

Usage for offline mode:
- Run the shadow jar or ./gradlew clean run
- Connect to mc.example.com._v1_8.viaaas.localhost

Usage for online mode with two accounts (recommended):
- Run the shadow jar or ./gradlew clean run
- You should set up a CORS Proxy (something like https://github.com/Rob--W/cors-anywhere) on local machine.
- Go to https://localhost:25543/auth.html, configure the CORS Proxy URL (something like http://localhost:8080/,
  note the ending slash) and listen to the username A that you're using to connect to the proxy.
- Add the account B you'll use in _u parameter to browser auth page.
- Connect to mc.example.com._v1_8._u(account B).viaaas.localhost
- Approve the login

Usage for online mode with one account:
- Run the shadow jar or ./gradlew clean run
- You should set up a CORS Proxy (something like https://github.com/Rob--W/cors-anywhere) on local machine.
- Go to https://localhost:25543/auth.html, configure the CORS Proxy URL (something like http://localhost:8080/,
  note the ending slash) and listen to the username.
- Add the account to browser auth page.
- Connect to mc.example.com._v1_8.viaaas.localhost
- Approve the login
- Minecraft client will give Bad Login after you approve the login in your browser. You can use
  https://www.curseforge.com/minecraft/mc-mods/auth-me for reauthenticate the client.

## WARNING
VIAaaS may trigger anti-cheats, due to block, item, movement and other differences between versions. USE AT OWN RISK

## FAQ
VIAaaS is stuck when connecting with online mode:
- Your system may have low entropy, see https://wiki.archlinux.org/index.php/Rng-tools
