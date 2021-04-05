package com.viaversion.aas.command

import com.viaversion.aas.command.sub.AspirinReloadSubCommand
import com.viaversion.aas.command.sub.ConnectionsSubCommand
import com.viaversion.aas.command.sub.StopSubCommand
import com.viaversion.aas.command.sub.VIAaaSSubCommand
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.commands.ViaCommandHandler

object ViaAspirinCommand : ViaCommandHandler(), Command {
    override val info = "ViaVersion command"

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