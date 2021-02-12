package com.github.creeper123123321.viaaas.command.sub

import com.github.creeper123123321.viaaas.viaaasVer
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.command.ViaSubCommand

object VIAaaSSubCommand : ViaSubCommand() {
    override fun name(): String = "viaaas"
    override fun description(): String = "Info about VIAaaS"
    override fun execute(p0: ViaCommandSender, p1: Array<out String>): Boolean {
        p0.sendMessage("VIAaaS version $viaaasVer")
        return true
    }
}