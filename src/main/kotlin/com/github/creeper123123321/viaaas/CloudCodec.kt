package com.github.creeper123123321.viaaas

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.handler.codec.ByteToMessageCodec
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.MessageToMessageCodec
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.exception.CancelDecoderException
import us.myles.ViaVersion.exception.CancelEncoderException
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher


object ChannelInit : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        val user = UserConnection(ch)
        CloudPipeline(user)
        ch.pipeline().addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            // "crypto"
            .addLast("frame", FrameCodec())
            // "compress" / dummy "decompress"
            .addLast("flow-handler", FlowControlHandler())
            .addLast("via-codec", CloudViaCodec(user))
            .addLast("handler", CloudMinecraftHandler(user, null, frontEnd = true))
    }
}

class CloudCrypto(val cipherDecode: Cipher, var cipherEncode: Cipher) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val i = msg.readerIndex()
        val size = msg.readableBytes()
        msg.writerIndex(i + cipherDecode.update(msg.nioBuffer(), msg.nioBuffer(i, cipherDecode.getOutputSize(size))))
        out.add(msg.retain())
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        val i = msg.readerIndex()
        val size = msg.readableBytes()
        msg.writerIndex(i + cipherEncode.update(msg.nioBuffer(), msg.nioBuffer(i, cipherEncode.getOutputSize(size))))
        out.add(msg.retain())
    }
}

class BackendInit(val user: UserConnection) : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        ch.pipeline().addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            // "crypto"
            .addLast("frame", FrameCodec())
            // compress
            .addLast("handler", CloudMinecraftHandler(user, null, frontEnd = false))
    }
}

class CloudCompressionCodec(val threshold: Int) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
    // https://github.com/Gerrygames/ClientViaVersion/blob/master/src/main/java/de/gerrygames/the5zig/clientviaversion/netty/CompressionEncoder.java
    private val inflater: Inflater =
        Inflater()// https://github.com/Gerrygames/ClientViaVersion/blob/master/src/main/java/de/gerrygames/the5zig/clientviaversion/netty/CompressionEncoder.java
    private val deflater: Deflater = Deflater()

    @Throws(Exception::class)
    override fun encode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        val frameLength = input.readableBytes()
        val outBuf = ctx.alloc().buffer()
        try {
            if (frameLength < threshold) {
                outBuf.writeByte(0)
                outBuf.writeBytes(input)
                out.add(outBuf.retain())
                return
            }
            Type.VAR_INT.writePrimitive(outBuf, frameLength)
            deflater.setInput(input.nioBuffer())
            deflater.finish()
            while (!deflater.finished()) {
                outBuf.ensureWritable(8192)
                val wIndex = outBuf.writerIndex()
                outBuf.writerIndex(wIndex + deflater.deflate(outBuf.nioBuffer(wIndex, outBuf.writableBytes())))
            }
            out.add(outBuf.retain())
        } finally {
            outBuf.release()
            deflater.reset()
        }
    }

    @Throws(Exception::class)
    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (input.isReadable) {
            val outLength = Type.VAR_INT.readPrimitive(input)
            if (outLength == 0) {
                out.add(input.retain())
                return
            }

            if (outLength < threshold) {
                throw DecoderException("Badly compressed packet - size of $outLength is below server threshold of $threshold")
            }
            if (outLength > 2097152) {
                throw DecoderException("Badly compressed packet - size of $outLength is larger than protocol maximum of 2097152")
            }

            inflater.setInput(input.nioBuffer())
            val output = ctx.alloc().buffer(outLength, outLength)
            try {
                output.writerIndex(output.writerIndex() + inflater.inflate(
                        output.nioBuffer(output.writerIndex(), output.writableBytes())))
                out.add(output.retain())
            } finally {
                inflater.reset()
                output.release()
            }
        }
    }

}

class FrameCodec : ByteToMessageCodec<ByteBuf>() {
    val badLength = DecoderException("Invalid length!")

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (!ctx.channel().isActive) {
            input.clear() // Ignore, should prevent DoS https://github.com/SpigotMC/BungeeCord/pull/2908
            return
        }

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
        Type.VAR_INT.writePrimitive(out, msg.readableBytes())
        out.writeBytes(msg)
    }
}

class CloudViaCodec(val info: UserConnection) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
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
}