package com.viaversion.aas.platform

import com.viaversion.aas.config.AspirinViaConfig
import com.viaversion.aas.initFuture
import com.viaversion.aas.viaaasVer
import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.netty.channel.DefaultEventLoop
import org.slf4j.LoggerFactory
import com.viaversion.viaversion.api.ViaAPI
import com.viaversion.viaversion.api.configuration.ViaVersionConfig
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.configuration.ConfigurationProvider
import com.viaversion.viaversion.api.platform.PlatformTask
import com.viaversion.viaversion.api.platform.ViaPlatform
import com.viaversion.viaversion.sponge.util.LoggerWrapper
import com.viaversion.viaversion.util.VersionInfo
import com.viaversion.viaversion.libs.gson.JsonObject
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

object AspirinPlatform : ViaPlatform<UUID> {
    val executor = Executors.newCachedThreadPool(ThreadFactoryBuilder().setNameFormat("Via-%d").setDaemon(true).build())
    val eventLoop = DefaultEventLoop(executor)

    init {
        eventLoop.execute(initFuture::join)
    }

    override fun sendMessage(p0: UUID, p1: String) {
        // todo
    }

    override fun onReload() {
    }
    
    override fun runSync(runnable: Runnable): AspirinTask = AspirinTask(eventLoop.submit(runnable))
    override fun runSync(p0: Runnable, p1: Long): AspirinTask =
        AspirinTask(eventLoop.schedule(p0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun runRepeatingSync(p0: Runnable, p1: Long): AspirinTask =
        AspirinTask(eventLoop.scheduleAtFixedRate(p0, 0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun cancelTask(p0: PlatformTask<*>?) {
        (p0 as AspirinTask).obj.cancel(false)
    }

    override fun getDump(): JsonObject = JsonObject()
    override fun kickPlayer(p0: UUID, p1: String): Boolean = false
    override fun getApi(): ViaAPI<UUID> = AspirinViaAPI
            override fun getDataFolder(): File = File("viaversion")
    override fun getConf(): ViaVersionConfig = AspirinViaConfig
    override fun runAsync(p0: Runnable): AspirinTask = AspirinTask(CompletableFuture.runAsync(p0, executor))
    override fun getLogger(): Logger = LoggerWrapper(LoggerFactory.getLogger("ViaVersion"))
    override fun getOnlinePlayers(): Array<ViaCommandSender> = arrayOf()
    override fun isPluginEnabled(): Boolean = true
    override fun getConfigurationProvider(): ConfigurationProvider = AspirinViaConfig
    override fun getPlatformName(): String = "VIAaaS"
    override fun getPlatformVersion(): String = viaaasVer
    override fun getPluginVersion(): String = VersionInfo.VERSION
    override fun isOldClientsAllowed(): Boolean = true
    override fun isProxy(): Boolean = true
}