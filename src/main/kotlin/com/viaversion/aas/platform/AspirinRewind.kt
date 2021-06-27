package com.viaversion.aas.platform

import de.gerrygames.viarewind.api.ViaRewindPlatform
import java.util.logging.Logger

object AspirinRewind : ViaRewindPlatform {
    override fun getLogger() = Logger.getLogger("ViaRewind")
}