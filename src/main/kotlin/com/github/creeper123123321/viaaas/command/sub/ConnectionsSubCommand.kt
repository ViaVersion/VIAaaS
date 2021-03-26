package com.github.creeper123123321.viaaas.command.sub

import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.parseProtocol
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.command.ViaSubCommand

object ConnectionsSubCommand : ViaSubCommand() {
    override fun name(): String = "connections"
    override fun description(): String = "Lists VIAaaS connections"
    override fun execute(p0: ViaCommandSender, p1: Array<out String>): Boolean {
        p0.sendMessage("List of player connections: ")
        Via.getPlatform().connectionManager.connections.forEach {
            val handler = it.channel?.pipeline()?.get(MinecraftHandler::class.java)
            val backAddr = handler?.endRemoteAddress
            val pVer = it.protocolInfo?.protocolVersion?.parseProtocol()
            val backName = it.protocolInfo?.username
            val backVer = it.protocolInfo?.serverProtocolVersion?.parseProtocol()
            val pAddr = handler?.data?.frontHandler?.endRemoteAddress
            p0.sendMessage("$pAddr $pVer -> $backVer ($backName) $backAddr")
        }
        return true
    }
}