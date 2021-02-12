package com.github.creeper123123321.viaaas.command.sub

import com.github.creeper123123321.viaaas.command.VIAaaSConsole
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.command.ViaSubCommand

object EndSubCommand : ViaSubCommand() {
    override fun name() = "stop"
    override fun description(): String = "Stops VIAaaS"
    override fun execute(p0: ViaCommandSender?, p1: Array<out String>?): Boolean {
        VIAaaSConsole.shutdown()
        return true
    }
}