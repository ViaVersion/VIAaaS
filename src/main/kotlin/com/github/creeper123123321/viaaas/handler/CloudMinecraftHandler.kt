package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.mcLogger
import com.github.creeper123123321.viaaas.setAutoRead
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import us.myles.ViaVersion.exception.CancelCodecException
import java.net.SocketAddress

class CloudMinecraftHandler(
    val data: ConnectionData,
    var other: Channel?,
    val frontEnd: Boolean
) : SimpleChannelInboundHandler<Packet>() {
    var remoteAddress: SocketAddress? = null

    override fun channelRead0(ctx: ChannelHandlerContext, packet: Packet) {
        if (ctx.channel().isActive) {
            data.state.handlePacket(this, ctx, packet)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        remoteAddress = ctx.channel().remoteAddress()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        other?.close()
        data.state.onInactivated(this)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        other?.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        other?.setAutoRead(ctx.channel().isWritable)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is CancelCodecException) return
        mcLogger.debug("Exception: ", cause)
        disconnect("Exception: $cause")
    }

    fun disconnect(s: String) {
        data.state.disconnect(this, s)
    }
}