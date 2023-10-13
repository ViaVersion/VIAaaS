package com.viaversion.aas.codec.packet.configuration;

import com.viaversion.aas.codec.packet.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class FinishConfig implements Packet {
	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
	}
}
