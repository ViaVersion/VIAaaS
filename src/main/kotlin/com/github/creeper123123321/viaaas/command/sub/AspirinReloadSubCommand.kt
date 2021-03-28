package com.github.creeper123123321.viaaas.command.sub

import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.command.ViaSubCommand

object AspirinReloadSubCommand: ViaSubCommand() {
    override fun name() = "aasreload"
    override fun description() = "Reloads VIAaaS config"

    override fun execute(sender: ViaCommandSender, args: Array<String>): Boolean {
        VIAaaSConfig.reloadConfig()
        sender.sendMessage("Reloaded VIAaaS config. Some configurations may need a restart.")
        return true
    }
}