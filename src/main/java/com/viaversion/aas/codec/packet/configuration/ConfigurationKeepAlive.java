package com.viaversion.aas.codec.packet.configuration;

import com.viaversion.aas.codec.packet.Packet;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class ConfigurationKeepAlive implements Packet {
	private long id;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		this.id = byteBuf.readLong();
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		byteBuf.writeLong(this.id);
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
}
