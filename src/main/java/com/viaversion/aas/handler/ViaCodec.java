package com.viaversion.aas.handler;

import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.exception.CancelDecoderException;
import com.viaversion.viaversion.exception.CancelEncoderException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ViaCodec extends MessageToMessageCodec<ByteBuf, ByteBuf> {
	private final UserConnection info;

	public ViaCodec(@NotNull UserConnection info) {
		this.info = info;
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
		if (!ctx.channel().isActive() ||!info.checkServerboundPacket(msg.readableBytes())) throw CancelEncoderException.generate(null);
		if (!info.shouldTransformPacket()) {
			out.add(msg.retain());
			return;
		}
		var transformedBuf = ctx.alloc().buffer().writeBytes(msg);
		try {
			info.transformServerbound(transformedBuf, CancelEncoderException::generate);
			out.add(transformedBuf.retain());
		} finally {
			transformedBuf.release();
		}
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
		if (!ctx.channel().isActive()) return;
		if (!info.checkClientboundPacket()) throw CancelDecoderException.generate(null);
		if (!info.shouldTransformPacket()) {
			out.add(msg.retain());
			return;
		}
		var transformedBuf = ctx.alloc().buffer().writeBytes(msg);
		try {
			info.transformClientbound(transformedBuf, CancelDecoderException::generate);
			out.add(transformedBuf.retain());
		} finally {
			transformedBuf.release();
		}
	}
}
