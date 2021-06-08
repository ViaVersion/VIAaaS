package com.viaversion.aas

import com.google.gson.JsonParser
import com.viaversion.aas.command.VIAaaSConsole
import com.viaversion.aas.command.ViaAspirinCommand
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.FrontEndInit
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.platform.*
import com.viaversion.aas.protocol.registerAspirinProtocols
import com.viaversion.aas.web.ViaWebApp
import com.viaversion.aas.web.WebDashboardServer
import com.viaversion.viaversion.ViaManagerImpl
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.data.MappingDataLoader
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import de.gerrygames.viarewind.api.ViaRewindConfigImpl
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.*
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
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.io.IoBuilder
import java.io.File
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.util.concurrent.CompletableFuture

val viaaasVer = JsonParser.parseString(
    AspirinPlatform::class.java.classLoader.getResourceAsStream("viaaas_info.json")!!.reader(Charsets.UTF_8).readText()
).asJsonObject.get("version").asString
var viaWebServer = WebDashboardServer()
var serverFinishing = CompletableFuture<Unit>()
var finishedFuture = CompletableFuture<Unit>()
val httpClient = HttpClient {
    install(UserAgent) {
        agent = "VIAaaS/$viaaasVer"
    }
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}
val initFuture = CompletableFuture<Unit>()
val bufferWaterMark = WriteBufferWaterMark(512 * 1024, 2048 * 1024)

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

fun channelServerSocketFactory(eventLoop: EventLoopGroup): ChannelFactory<ServerSocketChannel> {
    return when (eventLoop) {
        is EpollEventLoopGroup -> ChannelFactory { EpollServerSocketChannel() }
        is KQueueEventLoopGroup -> ChannelFactory { KQueueServerSocketChannel() }
        else -> ChannelFactory { NioServerSocketChannel() }
    }
}

fun channelSocketFactory(eventLoop: EventLoopGroup): ChannelFactory<SocketChannel> {
    return when (eventLoop) {
        is EpollEventLoopGroup -> ChannelFactory { EpollSocketChannel() }
        is KQueueEventLoopGroup -> ChannelFactory { KQueueSocketChannel() }
        else -> ChannelFactory { NioSocketChannel() }
    }
}

val parentLoop = eventLoopGroup()
val childLoop = eventLoopGroup()
var chFuture: ChannelFuture? = null
var ktorServer: NettyApplicationEngine? = null

fun main(args: Array<String>) {
    try {
        setupSystem()
        printSplash()
        generateCert()
        initVia()
        bindPorts(args)

        initFuture.complete(Unit)
        addShutdownHook()
        
        Thread { VIAaaSConsole.start() }.start()
        
        serverFinishing.join()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        stopServer()
    }
}

private fun addShutdownHook() {
    Runtime.getRuntime().addShutdownHook(Thread {
        serverFinishing.complete(Unit)
        finishedFuture.join()
    })
}

private fun setupSystem() {
    // Stolen from https://github.com/VelocityPowered/Velocity/blob/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/Velocity.java
    if (System.getProperty("io.netty.allocator.maxOrder") == null) {
        System.setProperty("io.netty.allocator.maxOrder", "9")
    }
    // Also stolen from Velocity
    System.setOut(IoBuilder.forLogger("STDOUT").setLevel(Level.INFO).buildPrintStream())
    System.setErr(IoBuilder.forLogger("STDERR").setLevel(Level.ERROR).buildPrintStream())
}

private fun printSplash() {
    println(
        """\\        // // //\\  =>     //||     //||   /=====/ PROXY
              | \\      // // //  \\       // ||    // ||  //
              |  \\    // // //====\\     //==||   //==||  \====\   $viaaasVer
              |   \\  // // //      \\   //   ||  //   ||      //
              |<=  \\// // //        \\ //    || //    || /====/""".trimMargin()
    )
}

private fun generateCert() {
    File("config/https.jks").apply {
        parentFile.mkdirs()
        if (!exists()) generateCertificate(this)
    }
}

private fun initVia() {
    Via.init(
        ViaManagerImpl.builder()
            .injector(AspirinInjector)
            .loader(AspirinLoader)
            .commandHandler(ViaAspirinCommand)
            .platform(AspirinPlatform).build()
    )
    MappingDataLoader.enableMappingsCache()
    (Via.getManager() as ViaManagerImpl).init()
    ProtocolVersion.register(-2, "AUTO")
    AspirinRewind.init(ViaRewindConfigImpl(File("config/viarewind.yml")))
    AspirinBackwards.init(File("config/viabackwards"))
    registerAspirinProtocols()
}

private fun bindPorts(args: Array<String>) {
    chFuture = ServerBootstrap()
        .group(parentLoop, childLoop)
        .channelFactory(channelServerSocketFactory(parentLoop))
        .childHandler(FrontEndInit)
        .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, bufferWaterMark)
        .childOption(ChannelOption.IP_TOS, 0x18)
        .childOption(ChannelOption.TCP_NODELAY, true)
        .bind(InetAddress.getByName(VIAaaSConfig.bindAddress), VIAaaSConfig.port)

    viaaasLogger.info("Binded minecraft into " + chFuture!!.sync().channel().localAddress())
    ktorServer = embeddedServer(Netty, commandLineEnvironment(args)) {}.start(false)
}

private fun stopServer() {
    try {
        Via.getManager().connectionManager.connections.forEach {
            it.channel?.pipeline()?.get(MinecraftHandler::class.java)?.disconnect("Stopping")
        }

        (Via.getManager() as ViaManagerImpl).destroy()
    } finally {
        finishedFuture.complete(Unit)
        ktorServer?.stop(1000, 1000)
        httpClient.close()
        listOf<Future<*>?>(
            chFuture?.channel()?.close(),
            parentLoop.shutdownGracefully(),
            childLoop.shutdownGracefully()
        )
            .forEach { it?.sync() }
    }
}

fun Application.mainWeb() {
    ViaWebApp().apply { main() }
}
