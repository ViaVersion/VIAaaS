package com.github.creeper123123321.viaaas.command

import com.github.creeper123123321.viaaas.command.sub.ConnectionsSubCommand
import com.github.creeper123123321.viaaas.command.sub.EndSubCommand
import com.github.creeper123123321.viaaas.command.sub.VIAaaSSubCommand
import us.myles.ViaVersion.commands.ViaCommandHandler

object AspirinCommands : ViaCommandHandler() {
    init {
        registerSubCommand(EndSubCommand)
        registerSubCommand(VIAaaSSubCommand)
        registerSubCommand(ConnectionsSubCommand)
    }
}