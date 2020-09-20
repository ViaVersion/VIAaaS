package com.github.creeper123123321.viaaas

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.*
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.exception.CancelCodecException
import us.myles.ViaVersion.exception.CancelDecoderException
import us.myles.ViaVersion.exception.CancelEncoderException
import us.myles.ViaVersion.util.PipelineUtil
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.Inflater


object ChannelInit : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        val user = UserConnection(ch)
        CloudPipeline(user)
        ch.pipeline().addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
                .addLast("frame-encoder", FrameEncoder)
                .addLast("frame-decoder", FrameDecoder())
                .addLast("compress", CloudCompressor())
                .addLast("decompress", CloudDecompressor())
                .addLast("flow-handler", FlowControlHandler())
                .addLast("via-encoder", CloudEncodeHandler(user))
                .addLast("via-decoder", CloudDecodeHandler(user))
                .addLast("handler", CloudSideForwarder(user, null))
    }
}

class BackendInit(val user: UserConnection) : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        ch.pipeline().addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
                .addLast("frame-encoder", FrameEncoder)
                .addLast("frame-decoder", FrameDecoder())
                .addLast("compress", CloudCompressor())
                .addLast("decompress", CloudDecompressor())
                .addLast("handler", CloudSideForwarder(user, null))
    }
}

class CloudDecompressor(var threshold: Int = -1) : MessageToMessageDecoder<ByteBuf>() {
    // https://github.com/Gerrygames/ClientViaVersion/blob/master/src/main/java/de/gerrygames/the5zig/clientviaversion/netty/CompressionEncoder.java
    private val inflater: Inflater = Inflater()

    @Throws(Exception::class)
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (threshold == -1) {
            out.add(input.retain())
            return
        }
        if (input.isReadable) {
            val outLength = Type.VAR_INT.readPrimitive(input)
            if (outLength == 0) {
                out.add(input.readBytes(input.readableBytes()))
            } else {
                if (outLength < threshold) {
                    throw DecoderException("Badly compressed packet - size of $outLength is below server threshold of $threshold")
                }
                if (outLength > 2097152) {
                    throw DecoderException("Badly compressed packet - size of $outLength is larger than protocol maximum of 2097152")
                }
                val temp = ByteArray(input.readableBytes())
                input.readBytes(temp)
                inflater.setInput(temp)
                val output = ByteArray(outLength)
                inflater.inflate(output)
                out.add(Unpooled.wrappedBuffer(output))
                inflater.reset()
            }
        }
    }

}

class CloudCompressor(var threshold: Int = -1) : MessageToByteEncoder<ByteBuf>() {
    // https://github.com/Gerrygames/ClientViaVersion/blob/master/src/main/java/de/gerrygames/the5zig/clientviaversion/netty/CompressionEncoder.java
    private val buffer = ByteArray(8192)
    private val deflater: Deflater = Deflater()

    @Throws(Exception::class)
    override fun encode(ctx: ChannelHandlerContext, input: ByteBuf, out: ByteBuf) {
        if (threshold == -1) {
            out.writeBytes(input)
            return
        }
        val frameLength = input.readableBytes()
        if (frameLength < threshold) {
            Type.VAR_INT.writePrimitive(out, 0)
            out.writeBytes(input)
        } else {
            Type.VAR_INT.writePrimitive(out, frameLength)
            val inBytes = ByteArray(frameLength)
            input.readBytes(inBytes)
            deflater.setInput(inBytes, 0, frameLength)
            deflater.finish()
            while (!deflater.finished()) {
                val written = deflater.deflate(buffer)
                out.writeBytes(buffer, 0, written)
            }
            deflater.reset()
        }
    }

}

@ChannelHandler.Sharable
object FrameEncoder : MessageToMessageEncoder<ByteBuf>() {
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
        if (length >= 2097152 || length < 0) throw DecoderException("Invalid length!")
        out.add(input.readRetainedSlice(length))
        checkpoint()
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