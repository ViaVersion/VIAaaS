package com.viaversion.aas.command.sub

import com.viaversion.aas.AspirinServer
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.command.ViaSubCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object VIAaaSSubCommand : ViaSubCommand() {
    override fun name(): String = "viaaas"
    override fun description(): String = "Info about VIAaaS"
    override fun execute(p0: ViaCommandSender, p1: Array<out String>): Boolean {
        p0.sendMessage("VIAaaS version ${AspirinServer.version}")
        CoroutineScope(Dispatchers.IO).launch { p0.sendMessage(AspirinServer.updaterCheckMessage()) }
        return true
    }
}