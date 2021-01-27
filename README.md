VIAaaS
---
How to use: server.example.com._p25565._v1_12_2._uBACKUSERNAME.viaaas.example.com (similar to tor to web proxies)

Default Minecraft: viaaas.localhost with port 25565
Default WS URL: wss://localhost:25543/ws

- VIAaaS auth page is designed for storing accounts in the player's browser local storage.
 It requires a CORS Proxy for calling Mojang APIs, which may make Mojang see that
 as suspicious and reset/block your account password if the IP address is suspect.

- There are some information about Mojang password resetting:
  https://github.com/GeyserMC/Geyser/wiki/Common-Issues#mojang-resetting-account-credentials and
  https://mobile.twitter.com/MojangSupport/status/863697596350517248

- VIAaaS may have security vulnerabilities, make sure to block the ports in firewall and take care of browser local storage.

Download: https://github.com/ViaVersion/VIAaaS/actions (needs to be logged into GitHub)

Requires Java 11

Usage for offline mode:
- Run the shadow jar or ./gradlew clean run
- Connect to mc.example.com._v1_8.viaaas.localhost

Usage for online mode:
- You can use two accounts (avoids Bad Login error) or the same account for front-end and back-end connections.
- Run the shadow jar or ./gradlew clean run
- You should set up a CORS Proxy (something like https://www.npmjs.com/package/cors-anywhere, ```git clone https://github.com/Rob--W/cors-anywhere && cd cors-anywhere && npm install && node server.js```) on the machine you are using the VIAaaS authenticator webpage.
- Go to VIAaaS auth webpage (https://localhost:25543/), configure the CORS Proxy URL (something like http://localhost:8080/,
  note the ending slash) and listen to the username A that you're using to connect to the proxy.
- Add the account B you'll use in ```_u``` parameter to browser auth page.
- Connect to ```mc.example.com._v1_8._u(account B).viaaas.localhost``` (```_u(account B)``` parameter can be removed if you are using the same account)
- Approve the login in auth webpage
- Minecraft client will give Bad Login after you approve the login in your browser if you are using the same account. You can use
  https://www.curseforge.com/minecraft/mc-mods/auth-me for reauthenticate the client.

## WARNING
VIAaaS may trigger anti-cheats, due to block, item, movement and other differences between versions. USE AT OWN RISK

## FAQ
VIAaaS is stuck when connecting with online mode:
- Your system may have low entropy, see https://wiki.archlinux.org/index.php/Rng-tools
