package com.viaversion.aas.codec

import com.velocitypowered.natives.compression.VelocityCompressor
import com.velocitypowered.natives.util.MoreByteBufUtils
import com.velocitypowered.natives.util.Natives
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.MessageToMessageCodec

class CompressionCodec(val threshold: Int) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
    // stolen from Krypton (GPL) and modified
    // https://github.com/astei/krypton/blob/master/src/main/java/me/steinborn/krypton/mod/shared/network/compression/MinecraftCompressEncoder.java
    private val VANILLA_MAXIMUM_UNCOMPRESSED_SIZE = 2 * 1024 * 1024 // 2MiB
    private val HARD_MAXIMUM_UNCOMPRESSED_SIZE = 16 * 1024 * 1024 // 16MiB
    private val UNCOMPRESSED_CAP =
        if (java.lang.Boolean.getBoolean("velocity.increased-compression-cap")) HARD_MAXIMUM_UNCOMPRESSED_SIZE else VANILLA_MAXIMUM_UNCOMPRESSED_SIZE
    private lateinit var compressor: VelocityCompressor

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        compressor = Natives.compress.get().create(6)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        compressor.close()
    }

    override fun encode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (!ctx.channel().isActive) return

        val outBuf = allocateBuffer(ctx, msg)
        try {
            val uncompressedSize = msg.readableBytes()
            if (uncompressedSize < threshold) { // Not compressed
                outBuf.writeByte(0)
                outBuf.writeBytes(msg)
            } else {
                Type.VAR_INT.writePrimitive(outBuf, uncompressedSize)
                val compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), compressor, msg)
                try {
                    compressor.deflate(compatibleIn, outBuf)
                } finally {
                    compatibleIn.release()
                }
            }
            out.add(outBuf.retain())
        } finally {
            outBuf.release()
        }
    }

    private fun allocateBuffer(ctx: ChannelHandlerContext, msg: ByteBuf): ByteBuf {
        val initialBufferSize = msg.readableBytes() + 1
        return MoreByteBufUtils.preferredBuffer(ctx.alloc(), compressor, initialBufferSize)
    }

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        if (!input.isReadable || !ctx.channel().isActive) return

        val claimedUncompressedSize = Type.VAR_INT.readPrimitive(input)
        if (claimedUncompressedSize == 0) { // Uncompressed
            out.add(input.retain())
            return
        }

        if (claimedUncompressedSize < threshold) {
            throw DecoderException("Badly compressed packet - size of $claimedUncompressedSize is below server threshold of $threshold")
        }
        if (claimedUncompressedSize > UNCOMPRESSED_CAP) {
            throw DecoderException("Badly compressed packet - size of $claimedUncompressedSize is larger than maximum of $UNCOMPRESSED_CAP")
        }
        val compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), compressor, input)
        val decompressed = MoreByteBufUtils.preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize)
        try {
            compressor.inflate(compatibleIn, decompressed, claimedUncompressedSize)
            input.clear()
            out.add(decompressed.retain())
        } finally {
            decompressed.release()
            compatibleIn.release()
        }
    }
}
