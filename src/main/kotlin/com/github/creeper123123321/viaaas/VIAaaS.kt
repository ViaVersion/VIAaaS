package com.github.creeper123123321.viaaas

import de.gerrygames.viarewind.api.ViaRewindConfigImpl
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFactory
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.kqueue.KQueueSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.concurrent.Future
import net.minecrell.terminalconsole.SimpleTerminalConsole
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.LineReaderBuilder
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.ViaManager
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.data.MappingDataLoader
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.util.Config
import us.myles.ViaVersion.util.GsonUtil
import us.myles.viaversion.libs.gson.JsonObject
import java.io.File
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.CompletableFuture

val viaaasVer = GsonUtil.getGson().fromJson(
    CloudPlatform::class.java.classLoader.getResourceAsStream("viaaas_info.json")!!
        .reader(Charsets.UTF_8).readText(), JsonObject::class.java
).get("version").asString

var runningServer = true
val viaaasLogger = LoggerFactory.getLogger("VIAaaS")

val httpClient = HttpClient {
    defaultRequest {
        header("User-Agent", "VIAaaS/$viaaasVer")
    }
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

val initFuture = CompletableFuture<Unit>()

// Minecraft doesn't have forward secrecy
val mcCryptoKey = KeyPairGenerator.getInstance("RSA").let {
    it.initialize(VIAaaSConfig.mcRsaSize) // https://stackoverflow.com/questions/1904516/is-1024-bit-rsa-secure
    it.genKeyPair()
}

val secureRandom = if (VIAaaSConfig.useStrongRandom) SecureRandom.getInstanceStrong() else SecureRandom()

fun eventLoopGroup(): EventLoopGroup {
    if (VIAaaSConfig.isNativeTransportMc) {
        if (Epoll.isAvailable()) return EpollEventLoopGroup()
        if (KQueue.isAvailable()) return KQueueEventLoopGroup()
    }
    return NioEventLoopGroup()
}

fun channelServerSocketFactory(): ChannelFactory<ServerSocketChannel> {
    if (VIAaaSConfig.isNativeTransportMc) {
        if (Epoll.isAvailable()) return ChannelFactory { EpollServerSocketChannel() }
        if (KQueue.isAvailable()) return ChannelFactory { KQueueServerSocketChannel() }
    }
    return ChannelFactory { NioServerSocketChannel() }
}

fun channelSocketFactory(): ChannelFactory<SocketChannel> {
    if (VIAaaSConfig.isNativeTransportMc) {
        if (Epoll.isAvailable()) return ChannelFactory { EpollSocketChannel() }
        if (KQueue.isAvailable()) return ChannelFactory { KQueueSocketChannel() }
    }
    return ChannelFactory { NioSocketChannel() }
}

fun main(args: Array<String>) {
    // Stolen from https://github.com/VelocityPowered/Velocity/blob/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/Velocity.java
    if (System.getProperty("io.netty.allocator.maxOrder") == null) {
        System.setProperty("io.netty.allocator.maxOrder", "9");
    }

    File("config/https.jks").apply {
        parentFile.mkdirs()
        if (!exists()) generateCertificate(this)
    }

    Via.init(
        ViaManager.builder()
            .injector(CloudInjector)
            .loader(CloudLoader)
            .commandHandler(CloudCommands)
            .platform(CloudPlatform).build()
    )
    MappingDataLoader.enableMappingsCache()
    Via.getManager().init()
    CloudRewind.init(ViaRewindConfigImpl(File("config/viarewind.yml")))
    CloudBackwards.init(File("config/viabackwards"))

    val parent = eventLoopGroup()
    val child = eventLoopGroup()

    val future = ServerBootstrap()
        .group(parent, child)
        .channelFactory(channelServerSocketFactory())
        .childHandler(ChannelInit)
        .childOption(ChannelOption.IP_TOS, 0x18)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .bind(InetAddress.getByName(VIAaaSConfig.bindAddress), VIAaaSConfig.port)

    var ktorServer: NettyApplicationEngine? = null
    try {
        viaaasLogger.info("Binded minecraft into " + future.sync().channel().localAddress())
        ktorServer = embeddedServer(Netty, commandLineEnvironment(args)) {}.start(false)
    } catch (e: Exception) {
        runningServer = false
        e.printStackTrace()
    }

    initFuture.complete(Unit)

    while (runningServer) {
        VIAaaSConsole().start()
    }

    ktorServer?.stop(1000, 1000)
    httpClient.close()
    listOf<Future<*>>(future.channel().close(), parent.shutdownGracefully(), child.shutdownGracefully())
        .forEach { it.sync() }

    Via.getManager().destroy()
}

class VIAaaSConsole : SimpleTerminalConsole(), ViaCommandSender {
    val commands = hashMapOf<String, (MutableList<String>?, String, Array<String>) -> Unit>()
    override fun isRunning(): Boolean = runningServer

    init {
        commands["stop"] = { suggestion, _, _ -> if (suggestion == null) this.shutdown() }
        commands["end"] = commands["stop"]!!
        commands["viaversion"] = { suggestion, _, args ->
            if (suggestion == null) {
                Via.getManager().commandHandler.onCommand(this, args)
            } else {
                suggestion.addAll(Via.getManager().commandHandler.onTabComplete(this, args))
            }
        }
        commands["viaver"] = commands["viaversion"]!!
        commands["vvcloud"] = commands["viaversion"]!!
        commands["help"] = { suggestion, _, _ ->
            if (suggestion == null) sendMessage(commands.entries.groupBy { it.value }.entries.joinToString(", ") {
                it.value.joinToString("/") { it.key }
            })
        }
        commands["?"] = commands["help"]!!
        commands["ver"] = { suggestion, _, _ ->
            if (suggestion == null) sendMessage(viaaasVer)
        }
        commands["list"] = { suggestion, _, _ ->
            if (suggestion == null) {
                sendMessage("List of player connections: ")
                Via.getPlatform().connectionManager.connections.forEach {
                    val pAddr = it.channel?.remoteAddress()
                    val pVer = it.protocolInfo?.protocolVersion?.let {
                        ProtocolVersion.getProtocol(it)
                    }
                    val backName = it.protocolInfo?.username
                    val backVer = it.protocolInfo?.serverProtocolVersion?.let {
                        ProtocolVersion.getProtocol(it)
                    }
                    val backAddr =
                        it.channel?.pipeline()?.get(CloudMinecraftHandler::class.java)?.other?.remoteAddress()
                    val pName = it.channel?.pipeline()?.get(CloudMinecraftHandler::class.java)?.data?.frontName
                    sendMessage("$pAddr ($pVer) ($pName) -> ($backVer) ($backName) $backAddr")
                }
            }
        }
    }

    override fun buildReader(builder: LineReaderBuilder): LineReader {
        // Stolen from Velocity
        return super.buildReader(builder.appName("VIAaaS").completer { _, line, candidates ->
            try {
                val cmdArgs = line.line().substring(0, line.cursor()).split(" ")
                val alias = cmdArgs[0]
                val args = cmdArgs.filterIndexed { i, _ -> i > 0 }
                if (cmdArgs.size == 1) {
                    candidates.addAll(commands.keys.filter { it.startsWith(alias, ignoreCase = true) }
                        .map { Candidate(it) })
                } else {
                    val cmd = commands[alias.toLowerCase()]
                    if (cmd != null) {
                        val suggestions = mutableListOf<String>()
                        cmd(suggestions, alias, args.toTypedArray())
                        candidates.addAll(suggestions.map(::Candidate))
                    }
                }
            } catch (e: Exception) {
                sendMessage("Error completing command: $e")
            }
        })
    }

    override fun runCommand(command: String) {
        val cmd = command.split(" ")
        try {
            val alias = cmd[0].toLowerCase()
            val args = cmd.subList(1, cmd.size).toTypedArray()
            val runnable = commands[alias]
            if (runnable == null) {
                sendMessage("unknown command, try 'help'")
            } else {
                runnable(null, alias, args)
            }
        } catch (e: Exception) {
            sendMessage("Error running command: $e")
        }
    }

    override fun shutdown() {
        viaaasLogger.info("Shutting down...")
        runningServer = false
    }


    override fun sendMessage(p0: String) {
        LoggerFactory.getLogger(this.name).info(p0)
    }

    override fun hasPermission(p0: String): Boolean = true
    override fun getUUID(): UUID = UUID.fromString(name)
    override fun getName(): String = "VIAaaS Console"
}

fun Application.mainWeb() {
    ViaWebApp().apply { main() }
}

object VIAaaSConfig : Config(File("config/viaaas.yml")) {
    init {
        reloadConfig()
    }

    override fun getUnsupportedOptions() = emptyList<String>().toMutableList()
    override fun getDefaultConfigURL() = VIAaaSConfig::class.java.classLoader.getResource("viaaas.yml")!!
    override fun handleConfig(p0: MutableMap<String, Any>?) {
    }

    val isNativeTransportMc: Boolean get() = this.getBoolean("native-transport-mc", true)
    val port: Int get() = this.getInt("port", 25565)
    val bindAddress: String get() = this.getString("bind-address", "localhost")!!
    val hostName: String get() = this.getString("host-name", "viaaas.localhost")!!
    val mcRsaSize: Int get() = this.getInt("mc-rsa-size", 4096)
    val useStrongRandom: Boolean get() = this.getBoolean("use-strong-random", true)
    val blockLocalAddress: Boolean get() = this.getBoolean("block-local-address", true)
    val requireHostName: Boolean get() = this.getBoolean("require-host-name", true)
    val defaultBackendPort: Int get() = this.getInt("default-backend-port", 25565)
}

class VIAaaSAddress {
    var protocol: Int? = null
    var viaSuffix: String? = null
    var realAddress: String? = null
    var port: Int? = null
    var online = true
    var altUsername: String? = null
    fun parse(address: String, viaHostName: String): VIAaaSAddress {
        val parts = address.split('.')
        var foundDomain = false
        var foundOptions = false
        val ourParts = StringBuilder()
        val realAddrBuilder = StringBuilder()
        for (i in parts.indices.reversed()) {
            val part = parts[i]
            var realAddrPart = false
            if (foundDomain) {
                if (!foundOptions) {
                    if (part.startsWith("_")) {
                        val arg = part.substring(2)
                        when {
                            part.startsWith("_p", ignoreCase = true) -> port = arg.toInt()
                            part.startsWith("_o", ignoreCase = true) -> online = arg.toBoolean()
                            part.startsWith("_v", ignoreCase = true) -> {
                                try {
                                    protocol = arg.toInt()
                                } catch (e: NumberFormatException) {
                                    val closest = ProtocolVersion.getClosest(arg.replace("_", "."))
                                    if (closest != null) {
                                        protocol = closest.version
                                    }
                                }
                            }
                            part.startsWith("_u", ignoreCase = true) -> {
                                if (arg.length > 16) throw IllegalArgumentException("Invalid alt username")
                                altUsername = arg
                            }
                        }
                    } else {
                        foundOptions = true
                    }
                }
                if (foundOptions) {
                    realAddrPart = true
                }
            } else if (parts.filterIndexed { a, _ -> a >= i }
                    .joinToString(".").equals(viaHostName, ignoreCase = true)) {
                foundDomain = true
            }
            if (realAddrPart) {
                realAddrBuilder.insert(0, "$part.")
            } else {
                ourParts.insert(0, "$part.")
            }
        }
        val realAddr = realAddrBuilder.toString().replace("\\.$".toRegex(), "")
        val suffix = ourParts.toString().replace("\\.$".toRegex(), "")
        if (realAddr.isEmpty()) {
            realAddress = address
        } else {
            realAddress = realAddr
            viaSuffix = suffix
        }
        return this
    }
}
