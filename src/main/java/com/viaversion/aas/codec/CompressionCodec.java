package com.viaversion.aas.codec;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.velocitypowered.natives.compression.JavaVelocityCompressor;
import com.velocitypowered.natives.compression.VelocityCompressor;
import com.velocitypowered.natives.util.MoreByteBufUtils;
import com.velocitypowered.natives.util.Natives;
import com.viaversion.aas.config.VIAaaSConfig;
import com.viaversion.aas.handler.MinecraftHandler;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.MessageToMessageCodec;
import kotlin.Pair;

import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DataFormatException;

public class CompressionCodec extends MessageToMessageCodec<ByteBuf, ByteBuf> {
	// stolen from Krypton (GPL) and modified
	// https://github.com/astei/krypton/blob/master/src/main/java/me/steinborn/krypton/mod/shared/network/compression/MinecraftCompressEncoder.java
	private static final int UNCOMPRESSED_CAP = 8 * 1024 * 1024; // 8MiB
	// Workaround for Lilypad backend servers
	private static final LoadingCache<SocketAddress, Pair<AtomicInteger, AtomicInteger>> nativeFails = CacheBuilder
			.newBuilder()
			.expireAfterWrite(2, TimeUnit.HOURS)
			.build(CacheLoader.from(() -> new Pair<>(new AtomicInteger(), new AtomicInteger())));
	private int threshold;
	private VelocityCompressor compressor;

	public CompressionCodec(int threshold) {
		this.threshold = threshold;
	}

	public void setThreshold(int threshold) {
		this.threshold = threshold;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) {
		var useNative = true;

		var attempts = getAttempts(ctx);
		if (attempts != null && Math.random() <= 0.95) {
			// We'll use Java when the native compression fail rate is high
			var probabilityNo = attempts.getSecond().get() + 1;
			var divisor = attempts.getFirst().get() + 2;
			useNative = !(Math.random() <= (double) probabilityNo / divisor);
		}

		var level = VIAaaSConfig.INSTANCE.getCompressionLevel();
		if (useNative) {
			compressor = Natives.compress.get().create(level);
		} else {
			compressor = JavaVelocityCompressor.FACTORY.create(level);
		}

		recordHandlerAdded(ctx);
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

	private Pair<AtomicInteger, AtomicInteger> getAttempts(ChannelHandlerContext ctx) {
		var handler = ctx.pipeline().get(MinecraftHandler.class);
		if (handler == null || handler.getFrontEnd() || !ctx.channel().isActive()) return null;
		var addr = handler.getEndRemoteAddress();

		return nativeFails.getUnchecked(addr);
	}

	private void recordHandlerAdded(ChannelHandlerContext ctx) {
		if (compressor instanceof JavaVelocityCompressor) return; // Only record errors happened with native

		var attempts = getAttempts(ctx);
		if (attempts != null) {
			attempts.getFirst().incrementAndGet();
		}
	}

	private void recordDecompressFailed(ChannelHandlerContext ctx) {
		if (compressor instanceof JavaVelocityCompressor) return; // Only record errors happened with native

		var attempts = getAttempts(ctx);
		if (attempts != null) {
			attempts.getSecond().incrementAndGet();
		}
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
		} catch (DataFormatException ex) {
			if (ex.getMessage().startsWith("Received a deflate stream that was too large, wanted ")) {
				return; // workaround for lilypad
			}
			recordDecompressFailed(ctx);
			throw ex;
		} finally {
			decompressed.release();
			compatibleIn.release();
		}
	}
}
