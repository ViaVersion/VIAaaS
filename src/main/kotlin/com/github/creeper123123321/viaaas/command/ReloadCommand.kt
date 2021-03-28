package com.github.creeper123123321.viaaas.command

import us.myles.ViaVersion.api.command.ViaCommandSender

object ReloadCommand : Command {
    override val info = "Alias for 'viaversion aasreload'"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        ViaAspirinCommand.execute(sender, alias, listOf("aasreload"))
    }
}