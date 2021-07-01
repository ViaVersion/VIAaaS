package com.viaversion.aas.codec.packet.common;

import com.viaversion.aas.codec.packet.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class AbstractEmpty implements Packet {
	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
	}
}
