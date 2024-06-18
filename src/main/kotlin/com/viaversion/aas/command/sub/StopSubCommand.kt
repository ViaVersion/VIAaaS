package com.viaversion.aas.command.sub

import com.viaversion.aas.AspirinServer
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.command.ViaSubCommand

object StopSubCommand : ViaSubCommand {
    override fun name() = "stop"
    override fun description(): String = "Stops VIAaaS"
    override fun execute(sender: ViaCommandSender, p1: Array<String>): Boolean {
        sender.sendMessage("Shutting down...")
        AspirinServer.stopSignal()
        return true
    }
}