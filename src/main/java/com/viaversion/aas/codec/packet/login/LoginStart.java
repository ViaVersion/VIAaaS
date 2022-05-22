package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.types.StringType;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class LoginStart implements Packet {
	private String username;
	private long timestamp;
	private byte[] key;
	private byte[] signature;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		username = new StringType(16).read(byteBuf);
		if (protocolVersion >= ProtocolVersion.v1_19.getVersion()) {
			if (byteBuf.readBoolean()) {
				timestamp = byteBuf.readLong();
				key = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf);
				signature = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf);
			}
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, username);
		if (protocolVersion >= ProtocolVersion.v1_19.getVersion()) {
			if (key == null) {
				byteBuf.writeBoolean(false);
			} else {
				byteBuf.writeBoolean(true);
				byteBuf.writeLong(timestamp);
				Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, key);
				Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, signature);
			}
		}
	}
}
