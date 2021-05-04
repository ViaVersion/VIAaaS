package com.viaversion.aas.codec

import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.MessageToMessageCodec
import java.util.zip.Deflater
import java.util.zip.Inflater

class CompressionCodec(val threshold: Int) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
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
        if (!input.isReadable || !ctx.channel().isActive) return
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
            output.writerIndex(
                output.writerIndex() + inflater.inflate(
                    output.nioBuffer(output.writerIndex(), output.writableBytes())
                )
            )
            out.add(output.retain())
        } finally {
            inflater.reset()
            output.release()
        }
    }

}