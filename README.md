VIAaaS
---

VIAaaS - ViaVersion ~~acetylsalicylic acid~~ as a Service - Standalone ViaVersion proxy

## How does it work?
- VIAaaS auth page stores account credentials in the player's browser local storage.
- Due to technical/security reasons, it requires a CORS Proxy for calling Mojang APIs, which may make Mojang see that
 as suspicious and reset/block your account password if the IP address seems suspect.
- Account credentials aren't sent to VIAaaS instance, though it's intermediated by CORS Proxy.
- VIAaaS receives a session hash from instance and then authenticates the session hash with Mojang.

## Setting up server instance
Download: [GitHub Actions](https://github.com/ViaVersion/VIAaaS/actions) (needs to be logged into GitHub) or [JitPack](https://jitpack.io/com/github/viaversion/viaaas/master-SNAPSHOT/viaaas-master-SNAPSHOT-all.jar)

How to start VIAaaS server:
- Requires Java 11
- ```java -jar VIAaaS-all.jar```
- Default Minecraft: ```viaaas.localhost``` with port 25565
- Default WS URL: ```wss://localhost:25543/ws```

## CORS Proxy
- For less chance of Mojang seeing the login as suspect, you (the player) should set up a CORS proxy on your machine.
- Note the ending slash in cors-anywhere address

Setting up [cors-anywhere](https://www.npmjs.com/package/cors-anywhere) on local machine:
- ```git clone https://github.com/Rob--W/cors-anywhere && cd cors-anywhere && npm install && node server.js```
- It will be available at ```http://localhost:8080/```

My cors-anywhere instance:
- If you trust me, you can use https://crp123-cors.herokuapp.com/ in https://viaversion.github.io/VIAaaS/ page.

## Usage for players
Usage for offline mode:
- Connect to ```mc.example.com.viaaas.localhost```

Usage for online mode:
- You can use two accounts (avoids Bad Login error), the same account for front-end and back-end connections, or use ```_of```
  (offline mode in frontend, unencrypted and no username verification).
- Go to VIAaaS auth webpage (https://localhost:25543/)
- Configure CORS proxy, see above in "CORS Proxy" section
- Listen to the username A you'll use to connect to the proxy.
- Add the account B to VIAaaS page which you'll use in ```_u(account B)``` parameter below.
- Connect to ```mc.example.com._u(account B).viaaas.localhost``` (```_u``` parameter can be removed if you are using the same username)
- Approve the login in auth webpage
- If you use the same online mode account, your client will show Bad Login. You can use a mod like
  [Auth Me](https://www.curseforge.com/minecraft/mc-mods/auth-me) or [ReAuth](https://www.curseforge.com/minecraft/mc-mods/reauth) for reauthenticating the client.

Example address: ```server.example.com._p25565._v1_12_2._ofalse._uBACKUSERNAME.viaaas.example.com``` (similar to [Tor2web](https://www.tor2web.org/) proxies)

Address parts:
- ```server.example.com```: backend server address
- ```_p```: backend port
- ```_v```: backend version ([protocol id](https://wiki.vg/Protocol_version_numbers) or name with underline instead of dots). ```AUTO``` is default and 1.8 is fallback if it fails.
- ```_o```: ```t``` to force online mode in frontend, ```f``` to disable online mode in frontend. If not set, it will be based on backend online mode.
- ```_u```: username to use in backend connection
- ```viaaas.example.com```: hostname suffix (defined in config)

## WARNING
- VIAaaS may trigger anti-cheats, due to block, item, movement and other differences between versions. USE AT OWN RISK
- VIAaaS server instance may have security vulnerabilities, make sure to block the ports in firewall
- Take care of browser local storage.
- Check the security of CORS proxy, it will intermediate Mojang API calls.
- Mojang may lock your account when API is called from a suspect IP address

## FAQ
VIAaaS is stuck when connecting with online mode:
- Your system may have low entropy, see https://wiki.archlinux.org/index.php/Rng-tools

My Microsoft account <18 years old is not able to log in, it's giving XSTS error:
- Add your account to a family (see https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_XSTS)

Why a online webpage for online mode?:
- It's easier to maintain in that way, because providing a chat with login requires encoding and decoding more packets which change through versions.
- It allows your account password and token to be kept with you

How to use IPv6?:
- When listening to 0.0.0.0, it should listen on IPv6 too.
- To use IPv6 in backend address, you need to use a instance with IPv6 connectivity. The hostname parser currently doesn't support direct IPv6, but you can use a DNS name.

How to use with Geyser?
- Currently you need to set all the parameters in Geyser's `address` field  
  e.g.:
  ```yml
  remote:
    # The IP address of the remote (Java Edition) server
    address: 2b2t.org._v1_12_2.viaaas.localhost
  ```
- Remember to use GeyserConnect if you are on a console
