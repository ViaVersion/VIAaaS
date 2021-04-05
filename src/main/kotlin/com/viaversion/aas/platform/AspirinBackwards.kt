package com.viaversion.aas.platform

import nl.matsv.viabackwards.api.ViaBackwardsPlatform
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.sponge.util.LoggerWrapper
import java.io.File
import java.util.logging.Logger

object AspirinBackwards : ViaBackwardsPlatform {
    val log = LoggerWrapper(LoggerFactory.getLogger("ViaBackwards"))
    override fun getDataFolder() = File("config/viabackwards")
    override fun getLogger(): Logger = log
    override fun disable() {
    }
}