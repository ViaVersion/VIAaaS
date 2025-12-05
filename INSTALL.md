## Pre-requisites
- Java 17 or higher
- Node.js and NPM (for compiling)
- Git (for compiling)

## Compiling or download

You can either download a pre-compiled JAR or build it from source

### Pre-compiled download

Download: [GitHub Releases](https://github.com/ViaVersion/VIAaaS/releases)
or [JitPack](https://jitpack.io/com/github/ViaVersion/VIAaaS/master-SNAPSHOT/VIAaaS-master-SNAPSHOT-all.jar)

### Compile manually

Clone the git repository
```sh
git clone https://github.com/ViaVersion/VIAaaS.git
cd VIAaaS/
```

Then build the JAR file with Gradle Wrapper. The result will be on 
`build/libs/` directory
```sh
./gradlew build
```

## How to start VIAaaS server
```sh
java -jar VIAaaS-all.jar
```

- Default Minecraft port is `25565` (ediable in config file)
- Default HTTPS port is `25543` (specify the port with CLI parameter ``-sslPort=<port>``)
- You can open a plain HTTP port with ``--port 8080``

### How to deploy the server

- You need a DNS wildcard pointing to the VIAaaS instance, like ``*.example.com -> 192.168.123.123``.
- Configure the hostname in the config
- Open the Minecraft port (default is 25565)
- Set up a reverse proxy like Apache or Nginx, proxying HTTPS (443) to the VIAaaS web port (default is 25543).
- You'll need to configure a Azure Client ID, edit ```config/web/js/config.js``` (default is in the jar) and
  configure your [Azure Client ID](https://minecraft.wiki/w/Microsoft_authentication).
- You may want to configure your own CORS Proxy, which is used for calling to Mojang API in web browser.
