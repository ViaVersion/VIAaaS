package com.viaversion.aas.platform

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.viaversion.aas.AspirinServer
import com.viaversion.aas.config.AspirinViaConfig
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.configuration.ConfigurationProvider
import com.viaversion.viaversion.api.configuration.ViaVersionConfig
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
    val executor = Executors.newCachedThreadPool(ThreadFactoryBuilder().setNameFormat("Via-%d").setDaemon(true).build())
    val eventLoop = DefaultEventLoop(executor)

    init {
        eventLoop.execute {
            AspirinServer.waitMainStart()
        }
    }

    override fun sendMessage(p0: UUID, p1: String) = Unit
    override fun onReload() = Unit
    override fun runSync(runnable: Runnable): AspirinTask = AspirinTask(eventLoop.submit(runnable))
    override fun runSync(p0: Runnable, p1: Long): AspirinTask =
        AspirinTask(eventLoop.schedule(p0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun runRepeatingSync(p0: Runnable, p1: Long): AspirinTask =
        AspirinTask(eventLoop.scheduleAtFixedRate(p0, 0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun getDump() = JsonObject()
    override fun kickPlayer(p0: UUID, p1: String) = false
    override fun getApi() = AspirinViaAPI
    override fun getDataFolder() = File("viaversion")
    override fun getConf(): ViaVersionConfig = AspirinViaConfig
    override fun runAsync(p0: Runnable): AspirinTask = AspirinTask(CompletableFuture.runAsync(p0, executor))
    override fun getLogger() = Logger.getLogger("ViaVersion")
    override fun getOnlinePlayers(): Array<ViaCommandSender> = arrayOf()
    override fun isPluginEnabled() = true
    override fun getConfigurationProvider(): ConfigurationProvider = AspirinViaConfig
    override fun getPlatformName() = "VIAaaS"
    override fun getPlatformVersion(): String = AspirinServer.version
    override fun getPluginVersion() = VersionInfo.VERSION
    override fun isOldClientsAllowed() = true
    override fun isProxy() = true
}
