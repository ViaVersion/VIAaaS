package com.viaversion.aas.handler

import com.viaversion.aas.mcLogger
import com.viaversion.aas.packet.Packet
import com.viaversion.aas.setAutoRead
import com.viaversion.viaversion.exception.CancelCodecException
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import java.net.SocketAddress
import java.nio.channels.ClosedChannelException

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
        if (cause is ClosedChannelException) return
        mcLogger.debug("Exception: ", cause)
        disconnect("Exception: $cause")
    }

    fun disconnect(s: String) {
        data.state.disconnect(this, s)
    }
}