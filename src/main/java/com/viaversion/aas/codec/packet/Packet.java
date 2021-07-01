package com.viaversion.aas.codec.packet;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

/**
 * A mutable object which represents a Minecraft packet data
 */
public interface Packet {
	void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception;

	void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception;
}
