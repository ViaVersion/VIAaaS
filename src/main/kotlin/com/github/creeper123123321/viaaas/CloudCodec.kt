package com.github.creeper123123321.viaaas

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.MessageToMessageDecoder
import io.netty.handler.codec.MessageToMessageEncoder
import io.netty.handler.codec.ReplayingDecoder
import io.netty.handler.timeout.ReadTimeoutHandler
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.ProtocolPipeline
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.exception.CancelCodecException
import us.myles.ViaVersion.exception.CancelDecoderException
import us.myles.ViaVersion.exception.CancelEncoderException
import us.myles.ViaVersion.packets.State
import us.myles.ViaVersion.util.PipelineUtil
import java.util.concurrent.TimeUnit

@ChannelHandler.Sharable
object FrameCoder : MessageToMessageEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val length = ctx.alloc().buffer(5)
        Type.VAR_INT.writePrimitive(length, msg.readableBytes())
        out.add(length)
        out.add(msg.retain())
    }
}

class FrameDecoder : ReplayingDecoder<ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val length = Type.VAR_INT.readPrimitive(input)
        if (length >= 2097152) throw IndexOutOfBoundsException()
        out.add(input.readRetainedSlice(length))
    }
}

object ChannelInit : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        ch.pipeline().addLast("frame-encoder", FrameCoder)
                .addLast("frame-decoder", FrameDecoder())
                .addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
                .addLast("handler", CloudSideForwarder(null))
        val user = UserConnection(ch)
        ProtocolPipeline(user)

        ch.pipeline().addBefore("encoder", "via-encoder", CloudEncodeHandler(user))
        ch.pipeline().addBefore("decoder", "via-decoder", CloudDecodeHandler(user))
    }
}

class CloudDecodeHandler(val info: UserConnection) : MessageToMessageDecoder<ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, bytebuf: ByteBuf, out: MutableList<Any>) {
        if (!info.checkIncomingPacket()) throw CancelDecoderException.generate(null)
        if (!info.shouldTransformPacket()) {
            out.add(bytebuf.retain())
            return
        }
        val transformedBuf: ByteBuf = ctx.alloc().buffer().writeBytes(bytebuf)
        try {
            if (info.protocolInfo!!.state == State.HANDSHAKE) {
                val id = Type.VAR_INT.readPrimitive(transformedBuf)
                Type.VAR_INT.writePrimitive(transformedBuf, id)
                if (id == 0 && info.get(CloudData::class.java) == null) {
                    val ver = Type.VAR_INT.readPrimitive(transformedBuf) // Client ver
                    Type.VAR_INT.writePrimitive(transformedBuf, ver)
                    val origAddr = Type.STRING.read(transformedBuf)
                    val addr = origAddr.split(" ")[0].split(".")
                    val port = Type.SHORT.readPrimitive(transformedBuf)
                }
            }

            info.transformIncoming(transformedBuf, CancelDecoderException::generate)
            out.add(transformedBuf.retain())
        } finally {
            transformedBuf.release()
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (PipelineUtil.containsCause(cause, CancelCodecException::class.java)) return
        super.exceptionCaught(ctx, cause)
    }
}

class CloudEncodeHandler(val info: UserConnection) : MessageToMessageEncoder<ByteBuf>() {
    override fun encode(ctx: ChannelHandlerContext, bytebuf: ByteBuf, out: MutableList<Any>) {
        info.checkOutgoingPacket()
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

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (PipelineUtil.containsCause(cause, CancelCodecException::class.java)) return
        super.exceptionCaught(ctx, cause)
    }
}