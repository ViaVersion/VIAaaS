package com.viaversion.aas.command

import com.viaversion.aas.AspirinServer
import com.viaversion.viaversion.api.command.ViaCommandSender

class EndCommand : Command {
    override val info = "Stops VIAaaS"
    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        sender.sendMessage("Shutting down...")
        AspirinServer.stopSignal()
    }
}