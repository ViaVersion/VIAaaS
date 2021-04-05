package com.viaversion.aas.codec

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToMessageCodec
import javax.crypto.Cipher

class CryptoCodec(val cipherDecode: Cipher, var cipherEncode: Cipher) : MessageToMessageCodec<ByteBuf, ByteBuf>() {
    override fun decode(ctx: ChannelHandlerContext, msg: ByteBuf, out: MutableList<Any>) {
        if (!ctx.channel().isActive) return
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