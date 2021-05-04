package com.viaversion.aas.platform

import com.viaversion.viabackwards.api.ViaBackwardsPlatform
import com.viaversion.viaversion.sponge.util.LoggerWrapper
import org.slf4j.LoggerFactory
import java.io.File
import java.util.logging.Logger

object AspirinBackwards : ViaBackwardsPlatform {
    val log = LoggerWrapper(LoggerFactory.getLogger("ViaBackwards"))
    override fun getDataFolder() = File("config/viabackwards")
    override fun getLogger(): Logger = log
    override fun disable() {
    }
}