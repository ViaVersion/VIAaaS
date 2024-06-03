package com.viaversion.aas.codec.packet.common;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractCookieRequest implements Packet {
	private String identifier;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		identifier = Type.STRING.read(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, identifier);
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}
}
