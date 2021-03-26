package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.mcLogger
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.packets.State

interface MinecraftConnectionState {
    val state: State
    fun handlePacket(
        handler: MinecraftHandler, ctx: ChannelHandlerContext,
        packet: Packet
    )

    fun disconnect(handler: MinecraftHandler, msg: String) {
        if (!handler.msgDisconnected) {
            handler.msgDisconnected = true
            mcLogger.info("DC ${handler.endRemoteAddress}: $msg")
        }
    }

    fun onInactivated(handler: MinecraftHandler) {
        mcLogger.info("- ${handler.endRemoteAddress}")
    }
}
