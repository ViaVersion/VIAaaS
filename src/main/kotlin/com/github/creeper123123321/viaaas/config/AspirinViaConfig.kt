package com.github.creeper123123321.viaaas.config

import us.myles.ViaVersion.AbstractViaConfig
import java.io.File
import java.net.URL

object AspirinViaConfig : AbstractViaConfig(File("config/viaversion.yml")) {
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