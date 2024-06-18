package com.viaversion.aas.command.sub

import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.command.ViaCommandSender
import com.viaversion.viaversion.api.command.ViaSubCommand

object ConnectionsSubCommand : ViaSubCommand {
    override fun name(): String = "connections"
    override fun description(): String = "Lists VIAaaS connections"
    override fun execute(p0: ViaCommandSender, p1: Array<out String>): Boolean {
        p0.sendMessage("List of player connections: ")
        Via.getManager().connectionManager.connections.forEach {
            val handler = it.channel?.pipeline()?.get(MinecraftHandler::class.java)
            val backAddr = handler?.endRemoteAddress
            val pVer = it.protocolInfo?.protocolVersion()
            val backName = it.protocolInfo?.username
            val backVer = it.protocolInfo?.serverProtocolVersion()
            val pAddr = handler?.data?.frontHandler?.endRemoteAddress
            p0.sendMessage("$pAddr $pVer -> $backVer ($backName) $backAddr")
        }
        return true
    }
}