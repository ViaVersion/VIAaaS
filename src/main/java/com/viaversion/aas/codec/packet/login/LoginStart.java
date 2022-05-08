package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.types.StringType;
import com.viaversion.viaversion.libs.opennbt.tag.builtin.CompoundTag;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class LoginStart implements Packet {
	private String username;
	private CompoundTag publicKey;

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
				publicKey = Type.NBT.read(byteBuf);
			}
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, username);
		if (protocolVersion >= ProtocolVersion.v1_19.getVersion()) {
			if (publicKey == null) {
				byteBuf.writeBoolean(false);
			} else {
				byteBuf.writeBoolean(true);
				Type.NBT.write(byteBuf, publicKey);
			}
		}
	}
}
