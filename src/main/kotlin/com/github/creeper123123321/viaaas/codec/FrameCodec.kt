package com.github.creeper123123321.viaaas.codec

import com.github.creeper123123321.viaaas.badLength
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageCodec
import us.myles.ViaVersion.api.type.Type

class FrameCodec : ByteToMessageCodec<ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (!ctx.channel().isActive) return
        // Ignore, should prevent DoS https://github.com/SpigotMC/BungeeCord/pull/2908

        val index = input.readerIndex()
        var nByte = 0
        val result = input.forEachByte {
            nByte++
            val hasNext = it.toInt().and(0x10000000) != 0
            if (nByte > 3) throw badLength
            hasNext
        }
        input.readerIndex(index)
        if (result == -1) return // not readable

        val length = Type.VAR_INT.readPrimitive(input)

        if (length >= 2097152 || length < 0) throw badLength
        if (!input.isReadable(length)) {
            input.readerIndex(index)
            return
        }

        out.add(input.readRetainedSlice(length))
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: ByteBuf) {
        if (msg.readableBytes() >= 2097152) throw badLength
        Type.VAR_INT.writePrimitive(out, msg.readableBytes())
        out.writeBytes(msg)
    }
}