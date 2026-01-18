package com.viaversion.aas.command

import com.viaversion.viaversion.api.command.ViaCommandSender

class ReloadCommand(val manager: CommandManager) : Command {
    override val info = "Executes reloading of VIAaaS"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        manager.getCommand("viaversion")!!.execute(sender, alias, listOf("reload"))
    }
}