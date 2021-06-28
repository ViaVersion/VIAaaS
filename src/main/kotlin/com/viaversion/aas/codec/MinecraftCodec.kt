package com.viaversion.aas.codec

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.PacketRegistry
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.Direction
import com.viaversion.viaversion.exception.CancelEncoderException
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec

class MinecraftCodec : MessageToMessageCodec<ByteBuf, Packet>() {
    override fun encode(ctx: ChannelHandlerContext, msg: Packet, out: MutableList<Any>) {
        if (!ctx.channel().isActive) throw CancelEncoderException.CACHED
        val buf = ByteBufAllocator.DEFAULT.buffer()
        try {
            val handler = ctx.pipeline().get(MinecraftHandler::class.java)
            PacketRegistry.encode(
                msg,
                buf,
                handler.data.frontVer!!,
                if (handler.frontEnd) Direction.CLIENTBOUND else Direction.SERVERBOUND
            )
            out.add(buf.retain())
        } finally {
            buf.release()
        }
    }

    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (!ctx.channel().isActive || !msg.isReadable) return
        val handler = ctx.pipeline().get(MinecraftHandler::class.java)
        out.add(
            PacketRegistry.decode(
                msg,
                handler.data.frontVer ?: 0,
                handler.data.state.state,
                if (handler.frontEnd) Direction.SERVERBOUND else Direction.CLIENTBOUND
            )
        )
        if (msg.isReadable) throw StacklessException("Remaining bytes!!!")
    }
}