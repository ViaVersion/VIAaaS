package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class LoginDisconnect implements Packet {
	private String msg;

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		msg = Type.STRING.read(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, msg);
	}
}
