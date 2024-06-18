package com.viaversion.aas.codec.packet.configuration;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class ConfigurationPluginMessage implements Packet {
	private String channel;
	private byte[] data;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		channel = Types.STRING.read(byteBuf);
		data = UtilKt.readRemainingBytes(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Types.STRING.write(byteBuf, channel);
		Types.REMAINING_BYTES.write(byteBuf, data);
	}
}
