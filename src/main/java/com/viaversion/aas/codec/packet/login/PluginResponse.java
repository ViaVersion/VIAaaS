package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class PluginResponse implements Packet {
	private int id;
	private boolean success;
	private byte[] data;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		id = Type.VAR_INT.readPrimitive(byteBuf);
		success = byteBuf.readBoolean();
		if (success) {
			data = UtilKt.readRemainingBytes(byteBuf);
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		Type.VAR_INT.writePrimitive(byteBuf, id);
		byteBuf.writeBoolean(success);
		if (success) {
			byteBuf.writeBytes(data);
		}
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public boolean isSuccess() {
		return success;
	}

	public void setSuccess(boolean success) {
		this.success = success;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}
}
