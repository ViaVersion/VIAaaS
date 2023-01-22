package com.viaversion.aas.platform

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.viaversion.aas.AspirinServer
import com.viaversion.aas.command.ViaAspirinCommand
import com.viaversion.aas.config.AspirinViaConfig
import com.viaversion.viaversion.ViaManagerImpl
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.data.MappingDataLoader
import com.viaversion.viaversion.api.platform.ViaPlatform
import com.viaversion.viaversion.libs.gson.JsonObject
import com.viaversion.viaversion.util.VersionInfo
import io.netty.channel.DefaultEventLoop
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

object AspirinPlatform : ViaPlatform<UUID> {
    private lateinit var conf: AspirinViaConfig
    val executor = Executors.newCachedThreadPool(
        ThreadFactoryBuilder()
            .setNameFormat("Via-%d")
            .setDaemon(true)
            .build()
    )
    val eventLoop = DefaultEventLoop(executor)
    private val logger = Logger.getLogger("ViaVersion")

    init {
        eventLoop.execute(AspirinServer::waitMainStart)
    }

    fun initVia(enableListener: Runnable) {
        Via.init(
            ViaManagerImpl.builder()
                .injector(AspirinInjector())
                .loader(AspirinLoader())
                .commandHandler(ViaAspirinCommand)
                .platform(AspirinPlatform).build()
        )
        conf = AspirinViaConfig()

        MappingDataLoader.enableMappingsCache()
        Via.getManager().addEnableListener(enableListener)
        (Via.getManager() as ViaManagerImpl).init()
    }

    override fun sendMessage(p0: UUID, p1: String) = Unit
    override fun onReload() = Unit
    override fun runSync(runnable: Runnable) = FutureTask(eventLoop.submit(runnable))
    override fun runSync(p0: Runnable, p1: Long) =
        FutureTask(eventLoop.schedule(p0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun runRepeatingSync(p0: Runnable, p1: Long) =
        FutureTask(eventLoop.scheduleAtFixedRate(p0, 0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun getDump() = JsonObject()
    override fun kickPlayer(p0: UUID, p1: String) = false
    override fun getApi() = AspirinApi()
    override fun getDataFolder() = File("config/viaversion")
    override fun getConf() = conf
    override fun runAsync(p0: Runnable) = FutureTask(CompletableFuture.runAsync(p0, executor))
    override fun getLogger() = logger
    override fun getOnlinePlayers(): Array<ViaCommandSender> = emptyArray()
    override fun isPluginEnabled() = true
    override fun getConfigurationProvider() = conf
    override fun getPlatformName() = "VIAaaS"
    override fun getPlatformVersion() = AspirinServer.version
    override fun getPluginVersion() = VersionInfo.VERSION
    override fun isOldClientsAllowed() = true
    override fun hasPlugin(name: String?)= false

    override fun isProxy() = true
}
