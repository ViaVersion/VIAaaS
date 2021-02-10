package com.github.creeper123123321.viaaas.codec

import com.github.creeper123123321.viaaas.handler.CloudMinecraftHandler
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.PacketRegistry
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

class MinecraftCodec : MessageToMessageCodec<ByteBuf, Packet>() {
    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        if (!ctx.channel().isActive) return
        val buf = ByteBufAllocator.DEFAULT.buffer()
        try {
            val handler = ctx.pipeline().get(CloudMinecraftHandler::class.java)
            PacketRegistry.encode(msg, buf, handler.data.frontVer!!)
            out.add(buf.retain())
        } finally {
            buf.release()
        }
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (!ctx.channel().isActive || !msg.isReadable) return
        val handler = ctx.pipeline().get(CloudMinecraftHandler::class.java)
        out.add(
            PacketRegistry.decode(
                msg,
                handler.data.frontVer ?: 0,
                handler.data.state.state, handler.frontEnd
            )
        )
        if (msg.isReadable) throw IllegalStateException("Remaining bytes!!!")
    }
}