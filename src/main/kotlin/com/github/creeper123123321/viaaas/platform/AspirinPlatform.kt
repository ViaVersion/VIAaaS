package com.github.creeper123123321.viaaas.platform

import com.github.creeper123123321.viaaas.config.AspirinViaConfig
import com.github.creeper123123321.viaaas.initFuture
import com.github.creeper123123321.viaaas.viaaasVer
import com.google.common.util.concurrent.ThreadFactoryBuilder
import io.netty.channel.DefaultEventLoop
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.api.ViaAPI
import us.myles.ViaVersion.api.ViaVersionConfig
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.configuration.ConfigurationProvider
import us.myles.ViaVersion.api.platform.TaskId
import us.myles.ViaVersion.api.platform.ViaConnectionManager
import us.myles.ViaVersion.api.platform.ViaPlatform
import us.myles.ViaVersion.sponge.util.LoggerWrapper
import us.myles.ViaVersion.util.VersionInfo
import us.myles.viaversion.libs.gson.JsonObject
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

object AspirinPlatform : ViaPlatform<UUID> {
    val connMan = ViaConnectionManager()
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
    
    override fun runSync(runnable: Runnable): TaskId = AspirinTask(eventLoop.submit(runnable))
    override fun runSync(p0: Runnable, p1: Long): TaskId =
        AspirinTask(eventLoop.schedule(p0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun runRepeatingSync(p0: Runnable, p1: Long): TaskId =
        AspirinTask(eventLoop.scheduleAtFixedRate(p0, 0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun cancelTask(p0: TaskId?) {
        (p0 as AspirinTask).obj.cancel(false)
    }

    override fun getDump(): JsonObject = JsonObject()
    override fun kickPlayer(p0: UUID, p1: String): Boolean = false
    override fun getApi(): ViaAPI<UUID> = AspirinViaAPI
    override fun getDataFolder(): File = File("viaversion")
    override fun getConf(): ViaVersionConfig = AspirinViaConfig
    override fun runAsync(p0: Runnable): TaskId = AspirinTask(CompletableFuture.runAsync(p0, executor))
    override fun getLogger(): Logger = LoggerWrapper(LoggerFactory.getLogger("ViaVersion"))
    override fun getConnectionManager(): ViaConnectionManager = connMan
    override fun getOnlinePlayers(): Array<ViaCommandSender> = arrayOf()
    override fun isPluginEnabled(): Boolean = true
    override fun getConfigurationProvider(): ConfigurationProvider = AspirinViaConfig
    override fun getPlatformName(): String = "VIAaaS"
    override fun getPlatformVersion(): String = viaaasVer
    override fun getPluginVersion(): String = VersionInfo.VERSION
    override fun isOldClientsAllowed(): Boolean = true
    override fun isProxy(): Boolean = true
}