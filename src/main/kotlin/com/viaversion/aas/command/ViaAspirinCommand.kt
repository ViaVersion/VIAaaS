package com.viaversion.aas.command

import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.commands.ViaCommandHandler

class ViaAspirinCommand : ViaCommandHandler(true), Command {
    override val info = "ViaVersion command"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return onTabComplete(sender, args.toTypedArray())
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        onCommand(sender, args.toTypedArray())
    }
}