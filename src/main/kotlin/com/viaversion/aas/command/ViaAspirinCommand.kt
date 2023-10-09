package com.viaversion.aas.command

import com.viaversion.aas.command.sub.ConnectionsSubCommand
import com.viaversion.aas.command.sub.StopSubCommand
import com.viaversion.aas.command.sub.VIAaaSSubCommand
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.commands.ViaCommandHandler

object ViaAspirinCommand : ViaCommandHandler(), Command {
    override val info = "ViaVersion command"

    init {
        registerSubCommand(StopSubCommand)
        registerSubCommand(VIAaaSSubCommand)
        registerSubCommand(ConnectionsSubCommand)
    }

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return onTabComplete(sender, args.toTypedArray())
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        onCommand(sender, args.toTypedArray())
    }
}