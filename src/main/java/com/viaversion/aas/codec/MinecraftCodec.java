package com.viaversion.aas.codec;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.aas.codec.packet.PacketRegistry;
import com.viaversion.aas.handler.MinecraftHandler;
import com.viaversion.aas.util.StacklessException;
import com.viaversion.viaversion.api.protocol.packet.Direction;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.exception.CancelEncoderException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MinecraftCodec extends MessageToMessageCodec<ByteBuf, Packet> {
	@Override
	protected void encode(@NotNull ChannelHandlerContext ctx, @NotNull Packet msg, @NotNull List<Object> out) {
		if (!ctx.channel().isActive()) throw CancelEncoderException.generate(null);

		var buf = ByteBufAllocator.DEFAULT.buffer();

		try {
			var handler = ctx.pipeline().get(MinecraftHandler.class);
			var version = handler.getData().getFrontVer();
			if (version == null) version = ProtocolVersion.unknown;
			PacketRegistry.INSTANCE.encode(
					msg,
					buf,
					version,
					handler.getFrontEnd() ? Direction.CLIENTBOUND : Direction.SERVERBOUND
			);
			out.add(buf.retain());
		} finally {
			buf.release();
		}
	}

	@Override
	protected void decode(@NotNull ChannelHandlerContext ctx, @NotNull ByteBuf msg, @NotNull List<Object> out) {
		if (!ctx.channel().isActive() || !msg.isReadable()) return;

		var handler = ctx.pipeline().get(MinecraftHandler.class);
		var frontVer = handler.getData().getFrontVer();
		if (frontVer == null) {
			frontVer = ProtocolVersion.unknown;
		}
		out.add(PacketRegistry.INSTANCE.decode(
				msg,
				frontVer,
				handler.getData().getState().getState(),
				handler.getFrontEnd() ? Direction.SERVERBOUND : Direction.CLIENTBOUND
		));
		if (msg.isReadable()) {
			UtilKt.getMcLogger().debug("Remaining bytes in packet {}", out);
			throw new StacklessException("Remaining bytes!!!");
		}
	}
}
