VIAaaS
---

VIAaaS - ViaVersion ~~acetylsalicylic acid~~ as a Service - Standalone ViaVersion proxy

Public instances: https://github.com/ViaVersion/VIAaaS/wiki/List-of-Public-Instances

Discord: https://viaversion.com/discord

Supported versions: https://viaversion.com/

## How does it work?

- [ViaVersion](https://viaversion.com), [ViaBackwards](https://viaversion.com/backwards)
  and [ViaRewind](https://viaversion.com/rewind) translates the connections to backend server.
- VIAaaS auth page stores account credentials in the player's browser local storage. Check for XSS vulnerabilities on
  your domain.
- Due to technical/security reasons, it requires a CORS Proxy for calling Mojang APIs, which may make Mojang see that as
  suspicious and reset/block your account password if the IP address seems suspect.
- Account credentials aren't sent to VIAaaS instance, though it's intermediated by CORS Proxy.
- VIAaaS receives a the session hash from instance and then the browser validates it to Mojang.

## Setting up server instance

Download: [GitHub Actions](https://github.com/ViaVersion/VIAaaS/actions) (needs to be logged into GitHub)
or [JitPack](https://jitpack.io/com/github/viaversion/viaaas/master-SNAPSHOT/viaaas-master-SNAPSHOT-all.jar)

How to start VIAaaS server:

```sh
java -jar VIAaaS-all.jar
```

- Requires Java 11
- Default Minecraft: ```via.localhost``` with port 25565
- Default HTTPS: ```https://localhost:25543/```
- Default WS URL: ```wss://localhost:25543/ws```

### How to create a public server

- You need a domain wildcarding to VIAaaS instance, like ``*.example.com -> 192.168.123.123``. You can
  use [DuckDNS](https://duckdns.org/) DDNS service.
- Configure the hostname in the config
- Open the Minecraft port (25565)
- The HTTPS page needs a certificate, you can use [Apache](https://httpd.apache.org/) (with
  a [Let's Encrypt](https://letsencrypt.org/) certificate) as a proxy. See apache_copypasta.txt file.

## CORS Proxy

- For less chance of Mojang seeing the login as suspect, you (the player) should set up a CORS proxy on your machine.
- Note the ending slash in cors-anywhere address
- You can use my public instance
  at https://crp123-cors.herokuapp.com/ ([source](https://github.com/creeper123123321/cors-anywhere/)) too, but proxies
  have a bit more chance of being seen as suspect.

Setting up [cors-anywhere](https://www.npmjs.com/package/cors-anywhere) on local machine:

```sh
git clone https://github.com/Rob--W/cors-anywhere
cd cors-anywhere
npm install
node server.js
```

- It will be available at ```http://localhost:8080/```

## Usage for players

Usage for offline mode:

- Connect to ```mc.example.net.via.localhost```

Usage for online mode:

- You can use two accounts (avoids Bad Login error), the same account for front-end and back-end connections, or
  use ```_of```
  (offline mode in frontend. May be useful if you have a client which is incompatible with online mode).
- Go to VIAaaS auth webpage (default is https://localhost:25543/)
- Configure CORS proxy, see above in "CORS Proxy" section
- Listen to the username A you'll use to connect to the proxy.
- Add the account B to VIAaaS page which you'll use in ```_u``` parameter below.
- Keep the page open
- Connect to ```mc.example.com._u(B).via.localhost``` (```_u``` can be removed if you are using the same username)
- Approve the login in the webpage
- If you use the same online mode account, your client may show Bad Login. You can use a mod like
  [Auth Me](https://www.curseforge.com/minecraft/mc-mods/auth-me)
  or [ReAuth](https://www.curseforge.com/minecraft/mc-mods/reauth).

### Address options

Example address:

- ```server.example.net._p25565._v1_12_2._of._uBACKUSERNAME.via.example.com```
- ```server.example.net.v_1_8.via.example.com```
- It's inspired by [Tor2web](https://www.tor2web.org/) proxies.

Address parts:

- You can use ``(option)_(value)`` too, like ``p_25565``.
- ```server.example.net```: backend server address
- ```_p```: backend port
- ```_v```: backend version ([protocol id](https://wiki.vg/Protocol_version_numbers) or name with underline instead of
  dots). ```AUTO``` is default and ``-1`` is fallback if it fails.
- ```_o```: ```t``` to force online mode in frontend, ```f``` to disable online mode in frontend. If not set, it will be
  based on backend online mode.
- ```_u```: username to use in backend connection
- ```via.example.com```: instance address (defined in config)

## WARNING

- VIAaaS may trigger anti-cheats, due to block, item, movement and other differences between versions. USE AT OWN RISK
- Take care of browser local storage. Check for XSS vulnerabilities on your domain.
- Check the security of CORS proxy, it will intermediate Mojang API calls.
- Mojang may lock your account when API is called from a suspect IP address

## FAQ

### Accounts

My Microsoft account <18 years old is not able to log in, it's giving XSTS error:

- Add your account to a family (see https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_XSTS)

Why a online webpage for online mode?:

- It's easier to maintain in that way, because providing login via chat requires encoding and decoding more packets
  which change through versions.
- It allows your account password and token to be kept with you.

How to use Microsoft Account?:

- If you are using a public VIAaaS instance, use this page https://viaversion.github.io/VIAaaS/ and configure the
  WebSocket address.

### Connection

How to use IPv6?:

- When listening to 0.0.0.0, it should listen on IPv6 too.
- To use IPv6 in backend address, you need to use a instance with IPv6 connectivity. The hostname parser currently
  doesn't support direct IPv6, but you can use a DNS name with https://sslip.io/

I'm getting a DNS error/"Unknown host" while connecting to (...).localhost

- Try configuring via-127-0-0-1.nip.io as hostname suffix

How to use with Geyser?

- Currently you need to set the parameters (at least the hostname) in Geyser's `address` field:
  ```yml
  remote:
    # The IP address of the remote (Java Edition) server
    address: 2b2t.org._v1_12_2.via.localhost
  ```
- If you are using a public GeyserConnect instance: connect to a publicly available VIAaaS instance,
  like ```mc.example.com.via.example.net``` as a Java Edition server.

Can I use it to connect to .onion Minecraft hidden servers?

- You can use .onion addresses if the instance is proxying the backend connections to TOR. Note that VIAaaS may log your
  requests.

Can you support more versions?

- See [DirtMultiVersion](https://github.com/DirtPowered/DirtMultiversion)
