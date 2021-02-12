package com.github.creeper123123321.viaaas.command.sub

import com.github.creeper123123321.viaaas.command.VIAaaSConsole
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.command.ViaCommandSender
import us.myles.ViaVersion.api.command.ViaSubCommand
import us.myles.ViaVersion.api.protocol.ProtocolVersion

object ConnectionsSubCommand : ViaSubCommand() {
    override fun name(): String = "connections"
    override fun description(): String = "Lists VIAaaS connections"
    override fun execute(p0: ViaCommandSender, p1: Array<out String>): Boolean {
        p0.sendMessage("List of player connections: ")
        Via.getPlatform().connectionManager.connections.forEach {
            val backAddr = it.channel?.remoteAddress()
            val pVer = it.protocolInfo?.protocolVersion?.let {
                ProtocolVersion.getProtocol(it)
            }
            val backName = it.protocolInfo?.username
            val backVer = it.protocolInfo?.serverProtocolVersion?.let {
                ProtocolVersion.getProtocol(it)
            }
            val pAddr =
                it.channel?.pipeline()?.get(MinecraftHandler::class.java)?.other?.remoteAddress()
            val pName = it.channel?.pipeline()?.get(MinecraftHandler::class.java)?.data?.frontName
            p0.sendMessage("$pAddr $pVer ($pName) -> $backVer ($backName) $backAddr")
        }
        return true
    }
}