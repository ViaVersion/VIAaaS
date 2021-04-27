package com.viaversion.aas.command.sub

import com.viaversion.aas.viaaasVer
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.command.ViaSubCommand

object VIAaaSSubCommand : ViaSubCommand() {
    override fun name(): String = "viaaas"
    override fun description(): String = "Info about VIAaaS"
    override fun execute(p0: ViaCommandSender, p1: Array<out String>): Boolean {
        p0.sendMessage("VIAaaS version $viaaasVer")
        return true
    }
}