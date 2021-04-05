package com.viaversion.aas.handler.state

import com.viaversion.aas.packet.Packet
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.mcLogger
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
