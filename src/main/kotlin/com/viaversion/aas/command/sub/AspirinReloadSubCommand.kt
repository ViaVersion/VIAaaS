package com.viaversion.aas.command.sub

import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.command.ViaSubCommand

object AspirinReloadSubCommand: ViaSubCommand() {
    override fun name() = "aasreload"
    override fun description() = "Reloads VIAaaS config"

    override fun execute(sender: ViaCommandSender, args: Array<String>): Boolean {
        VIAaaSConfig.reloadConfig()
        sender.sendMessage("Reloaded VIAaaS config. Some configurations may need a restart.")
        return true
    }
}