package com.viaversion.aas.codec.packet.common;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractCookieResponse implements Packet {
	private String id;
	private byte[] payload;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		id = Types.STRING.read(byteBuf);
		payload = Types.OPTIONAL_BYTE_ARRAY_PRIMITIVE.read(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Types.STRING.write(byteBuf, id);
		Types.OPTIONAL_BYTE_ARRAY_PRIMITIVE.write(byteBuf, payload);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public byte[] getPayload() {
		return payload;
	}

	public void setPayload(byte[] payload) {
		this.payload = payload;
	}
}
