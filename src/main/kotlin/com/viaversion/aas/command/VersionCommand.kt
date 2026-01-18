package com.viaversion.aas.command

import com.viaversion.aas.AspirinServer
import com.viaversion.viaversion.api.command.ViaCommandSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class VersionCommand : Command {
    override val info: String
        get() = "Shows VIAaaS version and checks for updates"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return listOf()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        sender.sendMessage("VIAaaS version ${AspirinServer.version}")
        CoroutineScope(Job()).launch { sender.sendMessage(AspirinServer.updaterCheckMessage()) }
    }
}