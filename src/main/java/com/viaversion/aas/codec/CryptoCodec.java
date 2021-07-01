package com.viaversion.aas.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Cipher;
import java.util.List;

public class CryptoCodec extends MessageToMessageCodec<ByteBuf, ByteBuf> {
	private final Cipher cipherEncode;
	private final Cipher cipherDecode;

	public CryptoCodec(@NotNull Cipher cipherEncode, @NotNull Cipher cipherDecode) {
		this.cipherEncode = cipherEncode;
		this.cipherDecode = cipherDecode;
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
		var i = msg.readerIndex();
		var size = msg.readableBytes();
		msg.writerIndex(i + cipherEncode.update(msg.nioBuffer(), msg.nioBuffer(i, cipherEncode.getOutputSize(size))));
		out.add(msg.retain());
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
		if (!ctx.channel().isActive()) return;
		var i = msg.readerIndex();
		var size = msg.readableBytes();
		msg.writerIndex(i + cipherDecode.update(msg.nioBuffer(), msg.nioBuffer(i, cipherDecode.getOutputSize(size))));
		out.add(msg.retain());
	}
}
