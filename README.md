VIAaaS
---

[![ViaVersion Discord](https://img.shields.io/badge/chat-on%20discord-blue.svg)](https://viaversion.com/discord)
[![powered by ViaVersion](https://img.shields.io/badge/powered%20by-ViaVersion-blue.svg)](https://viaversion.com/)


VIAaaS - ViaVersion as a Service

A cloud-based proxy for Minecraft Java Edition cross-version compatibility

<img alt="VIAaaS Logo (VIA logo with structural formula of aspirin)" height="200" src="https://viaversion.github.io/VIAaaS/src/main/resources/web/img/logo.webp">

VIAaaS allows players to connect to a Minecraft server using a different version than the server supports, by routing the connection through a proxy.
Instead of installing a mod, you simply join a generated hostname (domain) that contains the target server's details.

Address generator: https://jo0001.github.io/ViaSetup/aspirin

Public instances: https://github.com/ViaVersion/VIAaaS/wiki/List-of-Public-Instances

## Videos

Offline mode tutorial: https://youtu.be/lPdELnrxmp0

## How does it work?

- [ViaVersion](https://viaversion.com) translates the connections to backend server.

## How to run an instance

- Check [INSTALL.md](INSTALL.md)

## Usage for players

Replace ``via.localhost`` with the instance address and ``mc.example.com`` with the server you want to connect.

You can access the web page (default https://via.localhost:25543/) and fill the information of the server you
want to connect.

You can also specify which server you want to connect through address parameters
added as prefix in ``via.localhost``. The page will auto-generate it.

#### Offline mode:

- You can directly connect to ``mc.example.com.via.localhost``

#### Online mode:

Login with access token caching:

- Open the web page and go to "Accounts" tab and then add your account
- Click "Send access token", and then send it to the instance. After that you can close the page.
- Connect to ``mc.example.com.via.localhost`` with your account.


Fabric/Forge client:

- Install [OpenAuthMod](https://github.com/RaphiMC/OpenAuthMod) in your client.
- Join the server: ``mc.example.com.via.localhost``
- Approve the login


Web login (hard mode):

- Go to VIAaaS webpage
- Connect to ``link.via.localhost`` with your game and you'll receive a temporary listen token
- Click "Listen to logins" and fill your username, enable online mode and type the temp code
- Click "Accounts tab" and add your account
- Keep the page open
- Connect with your account to ``mc.example.com.via.localhost``
- Approve the login in the webpage


### Address options

#### Example address:

- ``server.example.net._p25565._v1_12_2._of._uBACKUSERNAME.via.example.com``
- ``server.example.net.v_1_8.via.example.com``

#### Address parts:

- You can use ``(option)_(value)`` too, like ``p_25565``.
- ```server.example.net```: backend server address
- ```_p```: backend port
- ```_v```: backend version ([protocol id](https://wiki.vg/Protocol_version_numbers) or name, replace ``.`` with ``_``)
  . ```AUTO``` is default (with 1.8 as fallback).
- ```_o```: ```true``` to force online mode in frontend, ```false``` to force offline mode in frontend. If not set, it
  will be based on backend online mode.
- ```_u```: username to use in backend connection (default is front-end username)
- ```via.example.com```: instance address (defined in config)

## WARNING

- VIAaaS may trigger anti-cheats, due to block, item, movement and other differences between versions. USE AT OWN RISK.

## FAQ

### Connection

#### I'm getting a DNS error/"Unknown host" while connecting to via.localhost

- Try configuring ```via.localho.st``` or ``127-0-0-1.nip.io`` as hostname suffix instead

#### How to use with Geyser?

- Set the parameters in Geyser's `address` field:
  ```yml
  remote:
    # The IP address of the remote (Java Edition) server
    address: 2b2t.org._v1_12_2.via.localhost
  ```
- If you are using GeyserConnect: connect to an available VIAaaS instance,
  like ```mc.example.com._v1_8.via.example.net``` as a Java Edition server.

#### Can you support more versions / Is there some alternative?

- See [ViaProxy](https://github.com/ViaVersion/ViaProxy)

#### Can I customize the files of HTTP server?

- Add files to ``config/web/`` directory
