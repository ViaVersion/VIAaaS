package com.github.creeper123123321.viaaas.platform

import de.gerrygames.viarewind.api.ViaRewindPlatform
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.sponge.util.LoggerWrapper
import java.util.logging.Logger

object CloudRewind : ViaRewindPlatform {
    val log = LoggerWrapper(LoggerFactory.getLogger("ViaRewind"))
    override fun getLogger(): Logger = log
}