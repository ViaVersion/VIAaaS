package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class PluginRequest implements Packet {
	private int id;
	private String channel;
	private byte[] data;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		id = Type.VAR_INT.readPrimitive(byteBuf);
		channel = Type.STRING.read(byteBuf);
		data = UtilKt.readRemainingBytes(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		Type.VAR_INT.writePrimitive(byteBuf, id);
		Type.STRING.write(byteBuf, channel);
		byteBuf.writeBytes(data);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getChannel() {
		return channel;
	}

	public void setChannel(String channel) {
		this.channel = channel;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}
}
