package com.viaversion.aas.codec.packet.common;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractCompression implements Packet {
	private int threshold;

	public int getThreshold() {
		return threshold;
	}

	public void setThreshold(int threshold) {
		this.threshold = threshold;
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		threshold = Types.VAR_INT.readPrimitive(byteBuf);
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Types.VAR_INT.writePrimitive(byteBuf, threshold);
	}
}
