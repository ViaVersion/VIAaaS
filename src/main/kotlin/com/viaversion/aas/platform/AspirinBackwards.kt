package com.viaversion.aas.platform

import com.viaversion.viabackwards.api.ViaBackwardsPlatform
import java.io.File
import java.util.logging.Logger

object AspirinBackwards : ViaBackwardsPlatform {
    override fun getDataFolder() = File("config/viabackwards")
    override fun getLogger() = Logger.getLogger("ViaBackwards")
    override fun disable() = Unit
}