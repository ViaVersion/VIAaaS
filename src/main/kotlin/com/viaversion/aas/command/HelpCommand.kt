package com.viaversion.aas.command

import com.viaversion.viaversion.api.command.ViaCommandSender

object HelpCommand : Command {
    override val info = "Lists the available commands"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        sender.sendMessage(
            VIAaaSConsole.commands.entries
                .groupBy { it.value }.entries
                .joinToString("\n") {
                    "${it.value.joinToString(", ") { it.key }}: ${it.key.info}"
                }
        )
    }
}