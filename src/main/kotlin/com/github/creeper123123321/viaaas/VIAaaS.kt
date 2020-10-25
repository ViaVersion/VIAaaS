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
import us.myles.ViaVersion.ViaManager
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.data.MappingDataLoader
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.util.Config
import java.io.File
import java.net.InetAddress

val httpClient = HttpClient {
    defaultRequest {
        header("User-Agent", "VIAaaS")
    }
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
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
    println("Binded minecraft into " + future.sync().channel().localAddress())

    val ktorServer = embeddedServer(Netty, commandLineEnvironment(args)) {}.start(false)

    loop@ while (true) {
        try {
            val cmd = readLine()?.trim()?.split(" ")
            when (cmd?.get(0)?.toLowerCase()) {
                "stop", "end" -> break@loop
                "viaversion", "viaver" -> Via.getManager().commandHandler.onCommand(CloudConsoleSender, cmd.subList(1, cmd.size)
                        .toTypedArray())
                else -> println("unknown command")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    ktorServer.stop(1000, 1000)
    httpClient.close()
    listOf<Future<*>>(future.channel().close(), boss.shutdownGracefully(), worker.shutdownGracefully())
            .forEach { it.sync() }

    Via.getManager().destroy()
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
    var protocol = 0
    var viaSuffix: String? = null
    var realAddress: String? = null
    var port = 0
    var online = false
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
