package com.github.creeper123123321.viaaas

import com.github.creeper123123321.viaaas.command.CloudCommands
import com.github.creeper123123321.viaaas.command.VIAaaSConsole
import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.handler.FrontEndInit
import com.github.creeper123123321.viaaas.platform.*
import com.github.creeper123123321.viaaas.web.ViaWebApp
import com.github.creeper123123321.viaaas.web.WebDashboardServer
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
import us.myles.ViaVersion.ViaManager
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.data.MappingDataLoader
import us.myles.ViaVersion.util.GsonUtil
import us.myles.viaversion.libs.gson.JsonObject
import java.io.File
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.util.concurrent.CompletableFuture

val viaaasVer = GsonUtil.getGson().fromJson(
    CloudPlatform::class.java.classLoader.getResourceAsStream("viaaas_info.json")!!.reader(Charsets.UTF_8).readText(),
    JsonObject::class.java
).get("version").asString
val viaWebServer = WebDashboardServer()
var runningServer = true
val httpClient = HttpClient {
    install(UserAgent) {
        agent = "VIAaaS/$viaaasVer"
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
        System.setProperty("io.netty.allocator.maxOrder", "9")
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
        .childHandler(FrontEndInit)
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

    VIAaaSConsole().start()

    ktorServer?.stop(1000, 1000)
    httpClient.close()
    listOf<Future<*>>(future.channel().close(), parent.shutdownGracefully(), child.shutdownGracefully())
        .forEach { it.sync() }

    Via.getManager().destroy()
}

fun Application.mainWeb() {
    ViaWebApp().apply { main() }
}