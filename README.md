VIAaaS
---
Idea: server.example.com._p25565._v1_12_2._otrue._uBACKUSERNAME.viaaas.example.com (default backend 25565 port and version
 default as auto, online-mode can be optional/required) (similar to tor to web proxies)

- TODO: _o option for disabling online mode only in front end, protocol auto detection

- Connection to private IP addresses are currently blocked

- VIAaaS auth page is designed for storing accounts in the browser local storage.
 It requires a CORS Proxy for calling Mojang APIs, which may make Mojang see that
 as suspicious and reset/block your account password.

- VIAaaS may have security vulnerabilities, make sure to block the ports in firewall and take care of browser local storage.

Usage for offline mode:
- Run the shadow jar or ./gradlew clean run
- Connect to mc.example.com._v1_8.viaaas.localhost

Usage for online mode (may block your Mojang account):
- Run the shadow jar or ./gradlew clean run
- You'll need 2 premium accounts for online mode (using only one account is possible but, as only one access tokens
 can be active, your Minecraft client will give Bad Login after you approve the login)
- Set up a CORS Proxy (something like https://github.com/Rob--W/cors-anywhere (less likely to look suspicious to
 Mojang if you run on your local machine) or https://github.com/Zibri/cloudflare-cors-anywhere (more suspicious)).
- Go to https://localhost:25543/auth.html, configure the CORS Proxy URL and listen to the username you're using to connect.
- Log in into Minecraft account with the username you'll use in _u option via browser.
- Connect to mc.example.com._v1_8.viaaas._u(BACKUSERNAME).localhost
- Approve the login
- There are some information about Mojang password resetting: https://github.com/GeyserMC/Geyser/wiki/Common-Issues#mojang-resetting-account-credentials
