package com.viaversion.aas.command

import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.command.ViaCommandSender

class ListCommand : Command {
    override val info = "Lists VIAaaS connections"

    override fun suggest(sender: ViaCommandSender, alias: String, args: List<String>): List<String> {
        return emptyList()
    }

    override fun execute(sender: ViaCommandSender, alias: String, args: List<String>) {
        val connections = Via.getManager().connectionManager.connections
        sender.sendMessage("List of player connections (${connections.size}): ")
        for (conn in connections) {
            val handler = conn.channel?.pipeline()?.get(MinecraftHandler::class.java)
            val backAddr = handler?.endRemoteAddress
            val pVer = conn.protocolInfo?.protocolVersion()
            val backName = conn.protocolInfo?.username
            val backVer = conn.protocolInfo?.serverProtocolVersion()
            val pAddr = handler?.data?.frontHandler?.endRemoteAddress
            sender.sendMessage("$pAddr $pVer -> $backVer ($backName) $backAddr")
        }
    }
}