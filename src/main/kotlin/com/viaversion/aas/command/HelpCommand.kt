package com.viaversion.aas.command

import com.viaversion.viaversion.api.command.ViaCommandSender

class HelpCommand(val cmdManager: CommandManager) : Command {
    override val info = "Lists the available commands"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        for (command in cmdManager.commands) {
            val aliases = cmdManager.getAliases(command)
            val msg = aliases.joinToString (", ") + ": " + command.info
            sender.sendMessage(msg)
        }
    }
}