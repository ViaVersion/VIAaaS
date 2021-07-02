package com.viaversion.aas.handler

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.mcLogger
import com.viaversion.aas.setAutoRead
import com.viaversion.viaversion.exception.CancelCodecException
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.proxy.ProxyConnectException
import io.netty.handler.proxy.ProxyHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.net.SocketAddress
import java.nio.channels.ClosedChannelException

class MinecraftHandler(
    val data: ConnectionData,
    val frontEnd: Boolean
) : SimpleChannelInboundHandler<Packet>() {
    lateinit var endRemoteAddress: SocketAddress
    val other: Channel? get() = if (frontEnd) data.backChannel else data.frontChannel
    var loggedDc = false
    val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun channelRead0(ctx: ChannelHandlerContext, packet: Packet) {
        if (!ctx.channel().isActive) return
        data.state.handlePacket(this, ctx, packet)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        endRemoteAddress = (ctx.channel().pipeline().get("proxy") as? ProxyHandler)?.destinationAddress()
            ?: ctx.channel().remoteAddress()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (!failedProxy(ctx)) {
            other?.close()
            data.state.onInactivated(this)
        }
        coroutineScope.cancel()
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        other?.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        other?.setAutoRead(ctx.channel().isWritable)
    }

    private fun failedProxy(ctx: ChannelHandlerContext): Boolean {
        // proxy connect future fails are handled in another part
        return (ctx.channel().pipeline().get("proxy") as? ProxyHandler)?.connectFuture()?.isSuccess == false
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is ProxyConnectException && failedProxy(ctx)) return
        if (cause is CancelCodecException) return
        if (cause is ClosedChannelException) return
        mcLogger.debug("Exception: ", cause)
        disconnect("$cause")
    }

    fun disconnect(s: String) {
        data.state.disconnect(this, s)
    }
}