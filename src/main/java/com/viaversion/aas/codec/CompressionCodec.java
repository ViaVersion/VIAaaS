package com.viaversion.aas.codec;

import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import com.velocitypowered.natives.util.Natives;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageCodec;

import java.util.List;

public class CompressionCodec extends MessageToMessageCodec<ByteBuf, ByteBuf> {
	// stolen from Krypton (GPL) and modified
	// https://github.com/astei/krypton/blob/master/src/main/java/me/steinborn/krypton/mod/shared/network/compression/MinecraftCompressEncoder.java
	private static final int UNCOMPRESSED_CAP = 8 * 1024 * 1024; // 8MiB
	private final int threshold;
	private VelocityCompressor compressor;

	public CompressionCodec(int threshold) {
		this.threshold = threshold;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) {
		compressor = Natives.compress.get().create(6);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) {
		compressor.close();
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
		if (!ctx.channel().isActive()) return;

		var outBuf = allocateBuffer(ctx, msg);
		try {
			var uncompressedSize = msg.readableBytes();
			if (uncompressedSize < threshold) { // Not compressed
				outBuf.writeByte(0);
				outBuf.writeBytes(msg);
			} else {
				Type.VAR_INT.writePrimitive(outBuf, uncompressedSize);
				var compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), compressor, msg);
				try {
					compressor.deflate(compatibleIn, outBuf);
				} finally {
					compatibleIn.release();
				}
			}
			out.add(outBuf.retain());
		} finally {
			outBuf.release();
		}
	}

	private ByteBuf allocateBuffer(ChannelHandlerContext ctx, ByteBuf msg) {
		var initialBufferSize = msg.readableBytes() + 1;
		return MoreByteBufUtils.preferredBuffer(ctx.alloc(), compressor, initialBufferSize);
	}

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf input, List<Object> out) throws Exception {
		if (!input.isReadable() || !ctx.channel().isActive()) return;

		var claimedUncompressedSize = Type.VAR_INT.readPrimitive(input);
		if (claimedUncompressedSize == 0) { // Uncompressed
			out.add(input.retain());
			return;
		}

		if (claimedUncompressedSize < threshold) {
			throw new DecoderException("Badly compressed packet - size of $claimedUncompressedSize is below server threshold of $threshold");
		}
		if (claimedUncompressedSize > UNCOMPRESSED_CAP) {
			throw new DecoderException("Badly compressed packet - size of $claimedUncompressedSize is larger than maximum of $UNCOMPRESSED_CAP");
		}
		var compatibleIn = MoreByteBufUtils.ensureCompatible(ctx.alloc(), compressor, input);
		var decompressed = MoreByteBufUtils.preferredBuffer(ctx.alloc(), compressor, claimedUncompressedSize);
		try {
			compressor.inflate(compatibleIn, decompressed, claimedUncompressedSize);
			input.clear();
			out.add(decompressed.retain());
		} finally {
			decompressed.release();
			compatibleIn.release();
		}
	}
}
