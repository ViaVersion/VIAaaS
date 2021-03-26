package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.mcLogger
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.setAutoRead
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import us.myles.ViaVersion.exception.CancelCodecException
import java.net.SocketAddress

class MinecraftHandler(
    val data: ConnectionData,
    val frontEnd: Boolean
) : SimpleChannelInboundHandler<Packet>() {
    lateinit var endRemoteAddress: SocketAddress
    val other: Channel? get() = if (frontEnd) data.backChannel else data.frontChannel
    var msgDisconnected = false

    override fun channelRead0(ctx: ChannelHandlerContext, packet: Packet) {
        if (ctx.channel().isActive) {
            data.state.handlePacket(this, ctx, packet)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        endRemoteAddress = ctx.channel().pipeline().get(Socks5ProxyHandler::class.java)?.destinationAddress()
            ?: ctx.channel().remoteAddress()
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