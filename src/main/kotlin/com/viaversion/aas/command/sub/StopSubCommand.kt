package com.viaversion.aas.command.sub

import com.viaversion.aas.serverFinishing
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.command.ViaSubCommand

object StopSubCommand : ViaSubCommand() {
    override fun name() = "stop"
    override fun description(): String = "Stops VIAaaS"
    override fun execute(sender: ViaCommandSender, p1: Array<String>): Boolean {
        sender.sendMessage("Shutting down...")
        serverFinishing.complete(Unit)
        return true
    }
}