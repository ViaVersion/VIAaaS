package com.viaversion.aas

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.velocitypowered.natives.util.Natives
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.FrontEndInit
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.platform.AspirinPlatform
import com.viaversion.aas.web.WebServer
import com.viaversion.viaversion.ViaManagerImpl
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.update.Version
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.network.tls.certificates.*
import io.ktor.serialization.gson.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import io.netty.resolver.dns.DnsNameResolverBuilder
import io.netty.util.concurrent.Future
import java.io.File
import java.lang.management.ManagementFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

object AspirinServer {
    var ktorServer: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    val version = JsonParser.parseString(
        AspirinPlatform::class.java.classLoader
            .getResourceAsStream("viaaas_info.json")!!
            .reader(Charsets.UTF_8)
            .readText()
    ).asJsonObject["version"].asString
    val cleanedVer get() = version.substringBefore("+")
    val viaWebServer = WebServer()
    private val serverFinishing = CompletableFuture<Unit>()
    private val finishedFuture = CompletableFuture<Unit>()
    private val initFuture = CompletableFuture<Unit>()
    val bufferWaterMark = WriteBufferWaterMark(512 * 1024, 2048 * 1024)

    // Minecraft crypto is very cursed: https://github.com/VelocityPowered/Velocity/issues/568
    var mcCryptoKey = generateKey()
    fun generateKey(): KeyPair {
        return KeyPairGenerator.getInstance("RSA").let {
            it.initialize(2048)
            it.genKeyPair()
        }
    }

    init {
        // This VIAaaS code idea is even more cursed
        AspirinPlatform.runRepeatingSync({
            mcCryptoKey = generateKey()
        }, 10 * 60 * 20L) // regenerate each 10 min
    }

    val parentLoop = eventLoopGroup()
    val childLoop = eventLoopGroup()
    var chFutures = mutableListOf<ChannelFuture>()
    val dnsResolver = DnsNameResolverBuilder(childLoop.next())
        .socketChannelFactory(channelSocketFactory(childLoop))
        .datagramChannelFactory(channelDatagramFactory(childLoop))
        .build()
    val httpClient = HttpClient(Java) {
        install(UserAgent) {
            agent = "VIAaaS/$cleanedVer"
        }
        install(ContentNegotiation) {
            gson()
        }
    }

    fun finish() {
        try {
            Via.getManager().connectionManager.connections.forEach {
                it.channel?.pipeline()?.get(MinecraftHandler::class.java)?.disconnect("Stopping")
            }

            (Via.getManager() as ViaManagerImpl).destroy()
        } finally {
            mainFinishSignal()
            ktorServer?.stop(1000, 1000)
            httpClient.close()
            (chFutures.map { it.channel().close() } + listOf<Future<*>?>(
                parentLoop.shutdownGracefully(),
                childLoop.shutdownGracefully()
            ))
                .forEach { it?.sync() }
        }
    }

    fun waitStopSignal() = serverFinishing.join()
    fun waitMainFinish() = finishedFuture.join()
    fun waitMainStart() = initFuture.join()

    fun wasStopSignalFired() = serverFinishing.isDone

    fun stopSignal() = serverFinishing.complete(Unit)
    fun mainFinishSignal() = finishedFuture.complete(Unit)
    fun mainStartSignal() = initFuture.complete(Unit)

    fun listenPorts(args: Array<String>) {
        val serverBootstrap = ServerBootstrap()
            .group(parentLoop, childLoop)
            .channelFactory(channelServerSocketFactory(parentLoop))
            .childHandler(FrontEndInit)
            .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, bufferWaterMark)
            .childOption(ChannelOption.IP_TOS, 0x18)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.TCP_FASTOPEN, 32)
        VIAaaSConfig.bindAddresses.forEach {
            chFutures.add(serverBootstrap.bind(it.host, it.port))
        }

        val commandLineCfg = CommandLineConfig(args)
        ktorServer = embeddedServer(factory = Netty, rootConfig = commandLineCfg.rootConfig) {
            takeFrom(commandLineCfg.engineConfig)
        }.start(false)

        viaaasLogger.info(
            "Using compression: {}, crypto: {}",
            Natives.compress.loadedVariant,
            Natives.cipher.loadedVariant
        )
        chFutures.forEach {
            viaaasLogger.info("Binded minecraft into {}", it.sync().channel().localAddress())
        }
        viaaasLogger.info(
            "Application started in " + ManagementFactory.getRuntimeMXBean().uptime
                .milliseconds.toDouble(DurationUnit.SECONDS) + "s"
        )
    }

    fun generateCert() {
        File("config/https.jks").apply {
            parentFile.mkdirs()
            if (!exists()) generateCertificate(this, keySizeInBits = 4096, algorithm = "SHA384withRSA")
        }
    }

    fun addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(Thread {
            stopSignal()
            waitMainFinish()
        })
    }

    fun currentPlayers(): Int {
        return Via.getManager().connectionManager.connections.count { it.protocolInfo.serverState == State.PLAY }
    }

    suspend fun updaterCheckMessage(): String {
        return try {
            val latestData = httpClient.get("https://api.github.com/repos/viaversion/viaaas/releases/latest")
                .body<JsonObject>()
            val latest = Version(latestData["tag_name"]!!.asString.removePrefix("v"))
            val current = Version(cleanedVer)
            when {
                latest > current -> "This build is outdated. Latest release version is $latest"
                latest < current -> "This build is newer than latest release version ($latest)."
                else -> "This build seems up to date."
            }
        } catch (e: Exception) {
            "Failed to fetch latest release info. $e"
        }
    }
}