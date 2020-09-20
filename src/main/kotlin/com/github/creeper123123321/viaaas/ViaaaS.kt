package com.github.creeper123123321.viaaas

import de.gerrygames.viarewind.api.ViaRewindConfigImpl
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.network.tls.certificates.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.ktor.websocket.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import kotlinx.coroutines.channels.consumeEach
import us.myles.ViaVersion.ViaManager
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.data.MappingDataLoader
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import java.io.File
import java.net.InetAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess


fun main(args: Array<String>) {
    val args = args.mapIndexed { i, content -> i to content }.toMap()
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
            .bind(InetAddress.getByName(args[0] ?: "::"), args[1]?.toIntOrNull() ?: 25565)

    println("Binded minecraft into " + future.sync().channel().localAddress())

    Thread { EngineMain.main(arrayOf()) }.start()

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

    future.channel().close().sync()
    boss.shutdownGracefully().sync()
    worker.shutdownGracefully().sync()
    Via.getManager().destroy()
    exitProcess(0) // todo what's stucking?
}

class ViaaaSAddress {
    var protocol = 0
    var viaSuffix: String? = null
    var realAddress: String? = null
    var port: Int = 25565
    var online: Boolean = false
    fun parse(address: String): ViaaaSAddress {
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
            } else if (part.equals("viaaas", ignoreCase = true)) {
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

fun Application.mainWeb() {
    ViaWebApp().apply { main() }
}

class ViaWebApp {
    data class WebSession(val id: String)

    val server = WebDashboardServer()

    fun Application.main() {
        install(DefaultHeaders)
        install(CallLogging)
        install(WebSockets) {
            pingPeriod = Duration.ofMinutes(1)
        }

        routing {
            webSocket("/ws") {
                server.connected(this)

                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            server.onMessage(this, frame.readText())
                        }
                    }
                } finally {
                    server.disconnected(this)
                }
            }

            static {
                defaultResource("auth.html", "web")
                resources("web")
            }

        }
    }
}

class WebDashboardServer {
    val clients = ConcurrentHashMap<WebSocketSession, WebClient>()
    suspend fun connected(ws: WebSocketSession) {
        clients[ws] = WebClient(ws, WebLogin())
    }

    suspend fun onMessage(ws: WebSocketSession, msg: String) {
        val client = clients[ws]!!
        client.state.onMessage(client, msg)
    }

    suspend fun disconnected(ws: WebSocketSession) {
        val client = clients[ws]!!
        client.state.disconnected(client)
        clients.remove(ws)
    }
}


data class WebClient(val ws: WebSocketSession, val state: WebState) {
}


interface WebState {
    fun onMessage(webClient: WebClient, msg: String)
    fun disconnected(webClient: WebClient)
}

class WebLogin : WebState {
    override fun onMessage(webClient: WebClient, msg: String) {
        TODO("Not yet implemented")
    }

    override fun disconnected(webClient: WebClient) {
        TODO("Not yet implemented")
    }
}


object CertificateGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        val jksFile = File("build/temporary.jks").apply {
            parentFile.mkdirs()
        }

        if (!jksFile.exists()) {
            generateCertificate(jksFile) // Generates the certificate
        }
    }
}
