package com.viaversion.aas.command

import com.viaversion.viaversion.api.command.ViaCommandSender

object ListCommand : Command {
    override val info = "Alias of 'viaaas connections'"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        ViaAspirinCommand.execute(sender, alias, listOf("connections"))
    }
}