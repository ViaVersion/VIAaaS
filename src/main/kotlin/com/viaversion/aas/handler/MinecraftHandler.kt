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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.net.SocketAddress
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ThreadLocalRandom

class MinecraftHandler(
    val data: ConnectionData,
    val frontEnd: Boolean
) : SimpleChannelInboundHandler<Packet>() {
    lateinit var endRemoteAddress: SocketAddress
    val other: Channel? get() = if (frontEnd) data.backChannel else data.frontChannel
    var loggedDc = false
    val coroutineScope = CoroutineScope(SupervisorJob())

    override fun channelRead0(ctx: ChannelHandlerContext, packet: Packet) {
        if (!ctx.channel().isActive) return
        data.state.handlePacket(this, ctx, packet)
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        endRemoteAddress = (ctx.pipeline()["proxy"] as? ProxyHandler)?.destinationAddress()
            ?: ctx.channel().remoteAddress()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        if (!failedProxy(ctx)) {
            data.state.onInactivated(this)
        }
        ctx.executor().execute(coroutineScope::cancel) // wait a bit... cancelexception spam...
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        other?.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        if (!ctx.channel().isWritable) {
            ctx.executor().execute(ctx.channel()::flush) // Flush to write more
        }
        other?.setAutoRead(ctx.channel().isWritable)
    }

    private fun failedProxy(ctx: ChannelHandlerContext): Boolean {
        // proxy connect future fails are handled in another part
        return (ctx.pipeline()["proxy"] as? ProxyHandler)?.connectFuture()?.isSuccess == false
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is ProxyConnectException && failedProxy(ctx)) return
        if (cause is CancelCodecException) return
        if (cause is ClosedChannelException) return
        val exceptionId = ThreadLocalRandom.current().nextInt().toUInt().toString(36)
        mcLogger.debug("Exception $exceptionId: ", cause)
        disconnect("$cause #$exceptionId")
    }

    fun disconnect(s: String) {
        data.state.disconnect(this, s)
    }
}