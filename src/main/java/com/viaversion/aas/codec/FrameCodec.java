package com.viaversion.aas.codec;

import com.viaversion.aas.UtilKt;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.exception.CancelDecoderException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FrameCodec extends ByteToMessageCodec<ByteBuf> {
	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		if (!ctx.channel().isActive()) {
			in.clear();
			// Netty throws an exception when there's no output
			throw CancelDecoderException.CACHED;
		}
		// Ignore, should prevent DoS https://github.com/SpigotMC/BungeeCord/pull/2908

		int index = in.readerIndex();
		AtomicInteger nByte = new AtomicInteger();
		int result = in.forEachByte(it -> {
			nByte.getAndIncrement();
			boolean hasNext = (it & 0x10000000) != 0;
			if (nByte.get() > 3) throw UtilKt.getBadLength();
			return hasNext;
		});
		in.readerIndex(index);
		if (result == -1) return; // not readable

		int length = Type.VAR_INT.readPrimitive(in);

		if (length >= 2097152 || length < 0) throw UtilKt.getBadLength();
		if (!in.isReadable(length)) {
			in.readerIndex(index);
			return;
		}

		out.add(in.readRetainedSlice(length));
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
		if (msg.readableBytes() >= 2097152) throw UtilKt.getBadLength();
		Type.VAR_INT.writePrimitive(out, msg.readableBytes());
		out.writeBytes(msg);
	}
}
