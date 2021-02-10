package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.handler.CloudMinecraftHandler
import com.github.creeper123123321.viaaas.mcLogger
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.packets.State

interface MinecraftConnectionState {
    val state: State
    fun handlePacket(
        handler: CloudMinecraftHandler, ctx: ChannelHandlerContext,
        packet: Packet
    )

    fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        mcLogger.info("Disconnected ${handler.remoteAddress}: $msg")
    }

    fun onInactivated(handler: CloudMinecraftHandler) {
        mcLogger.info(handler.remoteAddress?.toString() + " inactivated")
    }
}