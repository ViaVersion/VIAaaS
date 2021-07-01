package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.types.StringType;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class LoginStart implements Packet {
	private String username;

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		username = new StringType(16).read(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, username);
	}
}
