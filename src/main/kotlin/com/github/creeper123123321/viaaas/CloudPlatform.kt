package com.github.creeper123123321.viaaas

import com.google.common.util.concurrent.ThreadFactoryBuilder
import de.gerrygames.viarewind.api.ViaRewindPlatform
import io.netty.buffer.ByteBuf
import io.netty.channel.DefaultEventLoop
import nl.matsv.viabackwards.api.ViaBackwardsPlatform
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.AbstractViaConfig
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.ViaAPI
import us.myles.ViaVersion.api.ViaVersionConfig
import us.myles.ViaVersion.api.boss.BossBar
import us.myles.ViaVersion.api.boss.BossColor
import us.myles.ViaVersion.api.boss.BossStyle
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.configuration.ConfigurationProvider
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.platform.*
import us.myles.ViaVersion.api.protocol.ProtocolRegistry
import us.myles.ViaVersion.boss.CommonBoss
import us.myles.ViaVersion.bungee.providers.BungeeMovementTransmitter
import us.myles.ViaVersion.commands.ViaCommandHandler
import us.myles.ViaVersion.protocols.base.VersionProvider
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.MovementTransmitterProvider
import us.myles.ViaVersion.sponge.VersionInfo
import us.myles.ViaVersion.sponge.util.LoggerWrapper
import us.myles.ViaVersion.util.GsonUtil
import us.myles.viaversion.libs.gson.JsonObject
import java.io.File
import java.net.URL
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

object CloudBackwards : ViaBackwardsPlatform {
    val log = LoggerWrapper(LoggerFactory.getLogger("ViaBackwards"))
    override fun getDataFolder() = File("config/viabackwards")
    override fun getLogger(): Logger = log
    override fun disable() {
    }
}

object CloudRewind : ViaRewindPlatform {
    val log = LoggerWrapper(LoggerFactory.getLogger("ViaRewind"))
    override fun getLogger(): Logger = log
}

object CloudLoader : ViaPlatformLoader {
    override fun unload() {
    }

    override fun load() {
        Via.getManager().providers.use(MovementTransmitterProvider::class.java, BungeeMovementTransmitter())
        Via.getManager().providers.use(VersionProvider::class.java, CloudVersionProvider)
    }
}

object CloudCommands : ViaCommandHandler()
object CloudInjector : ViaInjector {
    override fun getEncoderName(): String = "via-codec"
    override fun getDecoderName() = "via-codec"
    override fun getDump(): JsonObject = JsonObject()

    override fun uninject() {
    }

    override fun inject() {
    }


    override fun getServerProtocolVersion() = 47 // Dummy
}

class CloudBossBar(title: String, health: Float, style: BossStyle, color: BossColor) :
    CommonBoss<Unit>(title, health, color, style)

object CloudAPI : ViaAPI<Unit> {
    override fun isInjected(p0: UUID): Boolean = false
    override fun createBossBar(p0: String, p1: BossColor, p2: BossStyle): BossBar<*> = CloudBossBar(p0, 0f, p2, p1)
    override fun createBossBar(p0: String, p1: Float, p2: BossColor, p3: BossStyle): BossBar<*> =
        CloudBossBar(p0, p1, p3, p2)

    override fun sendRawPacket(p0: Unit?, p1: ByteBuf?) {
        TODO("Not yet implemented")
    }

    override fun sendRawPacket(p0: UUID?, p1: ByteBuf?) {
        TODO("Not yet implemented")
    }

    override fun getPlayerVersion(p0: Unit?): Int {
        TODO("Not yet implemented")
    }

    override fun getPlayerVersion(p0: UUID?): Int {
        TODO("Not yet implemented")
    }

    override fun getVersion(): String = CloudPlatform.pluginVersion
    override fun getSupportedVersions(): SortedSet<Int> = ProtocolRegistry.getSupportedVersions()
}

object CloudPlatform : ViaPlatform<Unit> {
    val connMan = CloudConnectionManager()
    val executor = Executors.newCachedThreadPool(ThreadFactoryBuilder().setNameFormat("Via-%d").setDaemon(true).build())
    val eventLoop = DefaultEventLoop(executor)

    init {
        eventLoop.execute(initFuture::join)
    }

    override fun sendMessage(p0: UUID, p1: String) {
        // todo
    }

    override fun kickPlayer(p0: UUID, p1: String): Boolean = false // todo
    override fun getApi(): ViaAPI<Unit> = CloudAPI
    override fun getDataFolder(): File = File("viaversion")
    override fun getConf(): ViaVersionConfig = CloudConfig
    override fun onReload() {
    }

    override fun getDump(): JsonObject = JsonObject()
    override fun runSync(runnable: Runnable): TaskId = CloudTask(eventLoop.submit(runnable))
    override fun runSync(p0: Runnable, p1: Long): TaskId =
        CloudTask(eventLoop.schedule(p0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun runRepeatingSync(p0: Runnable, p1: Long): TaskId =
        CloudTask(eventLoop.scheduleAtFixedRate(p0, 0, p1 * 50L, TimeUnit.MILLISECONDS))

    override fun runAsync(p0: Runnable): TaskId = CloudTask(CompletableFuture.runAsync(p0, executor))
    override fun getLogger(): Logger = LoggerWrapper(LoggerFactory.getLogger("ViaVersion"))
    override fun getConnectionManager(): ViaConnectionManager = connMan
    override fun getOnlinePlayers(): Array<ViaCommandSender> = arrayOf()
    override fun cancelTask(p0: TaskId?) {
        (p0 as CloudTask).obj.cancel(false)
    }

    override fun isPluginEnabled(): Boolean = true
    override fun getConfigurationProvider(): ConfigurationProvider = CloudConfig

    override fun getPlatformName(): String = "VIAaaS"
    override fun getPlatformVersion(): String = viaaasVer
    override fun getPluginVersion(): String = VersionInfo.VERSION
    override fun isOldClientsAllowed(): Boolean = true
    override fun isProxy(): Boolean = true
}

class CloudConnectionManager : ViaConnectionManager() {
    override fun isFrontEnd(conn: UserConnection): Boolean = false
}

object CloudConfig : AbstractViaConfig(File("config/viaversion.yml")) {
    // https://github.com/ViaVersion/ViaFabric/blob/mc-1.16/src/main/java/com/github/creeper123123321/viafabric/platform/VRViaConfig.java
    override fun getDefaultConfigURL(): URL = javaClass.classLoader.getResource("assets/viaversion/config.yml")!!

    override fun handleConfig(config: Map<String, Any>) {
        // Nothing Currently
    }

    override fun getUnsupportedOptions(): List<String> = UNSUPPORTED
    override fun isAntiXRay(): Boolean = false
    override fun isItemCache(): Boolean = false
    override fun isNMSPlayerTicking(): Boolean = false
    override fun is1_12QuickMoveActionFix(): Boolean = false
    override fun getBlockConnectionMethod(): String = "packet"
    override fun is1_9HitboxFix(): Boolean = false
    override fun is1_14HitboxFix(): Boolean = false

    // Based on Sponge ViaVersion
    private val UNSUPPORTED = listOf(
        "anti-xray-patch", "bungee-ping-interval",
        "bungee-ping-save", "bungee-servers", "quick-move-action-fix", "nms-player-ticking",
        "item-cache", "velocity-ping-interval", "velocity-ping-save", "velocity-servers",
        "blockconnection-method", "change-1_9-hitbox", "change-1_14-hitbox"
    )

    init {
        // Load config
        reloadConfig()
    }
}

class CloudTask(val obj: Future<*>) : TaskId {
    override fun getObject(): Any = obj
}

object CloudVersionProvider : VersionProvider() {
    override fun getServerProtocol(connection: UserConnection): Int {
        val ver = connection.channel!!.pipeline().get(CloudMinecraftHandler::class.java).data.backVer
        if (ver != null) return ver
        return super.getServerProtocol(connection)
    }
}