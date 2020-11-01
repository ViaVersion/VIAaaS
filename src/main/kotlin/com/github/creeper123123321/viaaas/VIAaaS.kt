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
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.concurrent.Future
import net.minecrell.terminalconsole.SimpleTerminalConsole
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.ViaManager
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.data.MappingDataLoader
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.util.Config
import java.io.File
import java.lang.IllegalArgumentException
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.util.*
import java.util.concurrent.CompletableFuture

var runningServer = true
val viaaasLogger = LoggerFactory.getLogger("VIAaaS")

val httpClient = HttpClient {
    defaultRequest {
        header("User-Agent", "VIAaaS")
    }
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

val initFuture = CompletableFuture<Unit>()

// Minecraft doesn't have forward secrecy
val mcCryptoKey = KeyPairGenerator.getInstance("RSA").let {
    it.initialize(4096) // https://stackoverflow.com/questions/1904516/is-1024-bit-rsa-secure
    it.genKeyPair()
}

fun main(args: Array<String>) {
    File("config/https.jks").apply {
        parentFile.mkdirs()
        if (!exists()) generateCertificate(this)
    }

    Via.init(ViaManager.builder()
            .injector(CloudInjector)
            .loader(CloudLoader)
            .commandHandler(CloudCommands)
            .platform(CloudPlatform).build())
    MappingDataLoader.enableMappingsCache()
    Via.getManager().init()
    CloudRewind.init(ViaRewindConfigImpl(File("config/viarewind.yml")))
    CloudBackwards.init(File("config/viabackwards.yml"))

    val boss = NioEventLoopGroup()
    val worker = NioEventLoopGroup()
    val future = ServerBootstrap().group(boss, worker)
            .channel(NioServerSocketChannel::class.java)
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
    listOf<Future<*>>(future.channel().close(), boss.shutdownGracefully(), worker.shutdownGracefully())
            .forEach { it.sync() }

    Via.getManager().destroy()
}

class VIAaaSConsole : SimpleTerminalConsole(), ViaCommandSender {
    val commands = hashMapOf<String, (String, Array<String>) -> Unit>()
    override fun isRunning(): Boolean = runningServer

    init {
        commands["stop"] = { _, _ -> this.shutdown() }
        commands["end"] = commands["stop"]!!
        commands["viaversion"] = { _, args ->
            Via.getManager().commandHandler.onCommand(this, args)
        }
        commands["viaver"] = commands["viaversion"]!!
        commands["vvcloud"] = commands["viaversion"]!!
        commands["help"] = { _, _ ->
            sendMessage(commands.keys.toString())
        }
        commands["?"] = commands["help"]!!
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
                runnable(alias, args)
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

    val port: Int get() = this.getInt("port", 25565)
    val bindAddress: String get() = this.getString("bind-address", "localhost")!!
    val hostName: String get() = this.getString("host-name", "viaaas.localhost")!!
}

class VIAaaSAddress {
    var protocol: Int? = null
    var viaSuffix: String? = null
    var realAddress: String? = null
    var port: Int? = null
    var online = true
    var altUsername : String? = null
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
                                        protocol = closest.id
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
