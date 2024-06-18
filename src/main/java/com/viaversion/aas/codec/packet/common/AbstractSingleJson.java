package com.viaversion.aas.codec.packet.common;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractSingleJson implements Packet {
	private JsonElement msg;

	public JsonElement getMsg() {
		return msg;
	}

	public void setMsg(JsonElement msg) {
		this.msg = msg;
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		msg = JsonParser.parseString(Types.STRING.read(byteBuf));
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Types.STRING.write(byteBuf, msg.toString());
	}
}
