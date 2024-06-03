package com.viaversion.aas.codec.packet.common;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractPing implements Packet {
	private long number;

	public long getNumber() {
		return number;
	}

	public void setNumber(long number) {
		this.number = number;
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		number = byteBuf.readLong();
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		byteBuf.writeLong(number);
	}
}
