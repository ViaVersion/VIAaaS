package com.github.creeper123123321.viaaas.handler

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.exception.CancelDecoderException
import us.myles.ViaVersion.exception.CancelEncoderException

class ViaCodec(val info: UserConnection) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, bytebuf: ByteBuf, out: MutableList<Any>) {
        if (!info.checkIncomingPacket()) throw CancelDecoderException.generate(null)
        if (!info.shouldTransformPacket()) {
            out.add(bytebuf.retain())
            return
        }
        val transformedBuf: ByteBuf = ctx.alloc().buffer().writeBytes(bytebuf)
        try {
            info.transformIncoming(transformedBuf, CancelDecoderException::generate)
            out.add(transformedBuf.retain())
        } finally {
            transformedBuf.release()
        }
    }

    override fun encode(ctx: ChannelHandlerContext, bytebuf: ByteBuf, out: MutableList<Any>) {
        if (!info.checkOutgoingPacket()) throw CancelEncoderException.generate(null)
        if (!info.shouldTransformPacket()) {
            out.add(bytebuf.retain())
            return
        }
        val transformedBuf: ByteBuf = ctx.alloc().buffer().writeBytes(bytebuf)
        try {
            info.transformOutgoing(transformedBuf, CancelEncoderException::generate)
            out.add(transformedBuf.retain())
        } finally {
            transformedBuf.release()
        }
    }
}
