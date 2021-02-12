package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.UnknownPacket
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.packets.State

object PlayState : MinecraftConnectionState {
    override val state: State
        get() = State.PLAY

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if ((packet as UnknownPacket).id !in 0..127) throw IllegalArgumentException("Invalid packet id!")
        forward(handler, packet)
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close()
    }
}