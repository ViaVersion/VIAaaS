package com.viaversion.aas.codec.packet.configuration;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class ConfigurationPluginMessage implements Packet {
	private String channel;
	private byte[] data;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		channel = Type.STRING.read(byteBuf);
		data = UtilKt.readRemainingBytes(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, int protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, channel);
		Type.REMAINING_BYTES.write(byteBuf, data);
	}
}
