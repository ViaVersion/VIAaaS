package com.viaversion.aas.command

import com.viaversion.viaversion.api.command.ViaCommandSender

object EndCommand : Command {
    override val info = "Alias of 'viaaas stop'"
    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        ViaAspirinCommand.execute(sender, alias, listOf("stop"))
    }
}