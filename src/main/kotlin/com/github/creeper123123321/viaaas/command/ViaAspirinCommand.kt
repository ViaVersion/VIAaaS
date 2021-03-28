package com.github.creeper123123321.viaaas.command

import com.github.creeper123123321.viaaas.command.sub.AspirinReloadSubCommand
import com.github.creeper123123321.viaaas.command.sub.ConnectionsSubCommand
import com.github.creeper123123321.viaaas.command.sub.StopSubCommand
import com.github.creeper123123321.viaaas.command.sub.VIAaaSSubCommand
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.commands.ViaCommandHandler

object ViaAspirinCommand : ViaCommandHandler(), Command {
    override val info = "ViaVersion commands"

    init {
        registerSubCommand(StopSubCommand)
        registerSubCommand(VIAaaSSubCommand)
        registerSubCommand(ConnectionsSubCommand)
        registerSubCommand(AspirinReloadSubCommand)
    }

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return onTabComplete(sender, args.toTypedArray())
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        onCommand(sender, args.toTypedArray())
    }
}