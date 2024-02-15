package com.viaversion.aas.codec.packet.play;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class PluginMessage implements Packet {
	private String channel;
	private byte[] data;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		channel = Type.STRING.read(byteBuf);
		if (protocolVersion.olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
			data = UtilKt.readByteArray(byteBuf, readExtendedForgeShort(byteBuf));
		} else {
			data = UtilKt.readRemainingBytes(byteBuf);
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, channel);
		if (protocolVersion.olderThanOrEqualTo(ProtocolVersion.v1_7_6)) {
			writeExtendedForgeShort(byteBuf, data.length);
		}
		byteBuf.writeBytes(data);
	}

	// stolen from https://github.com/VelocityPowered/Velocity/blob/27ccb9d387fc9a0aecd5c4b570d7d957558efddc/proxy/src/main/java/com/velocitypowered/proxy/protocol/ProtocolUtils.java#L418
	private int readExtendedForgeShort(ByteBuf buf) {
		int low = buf.readUnsignedShort();
		int high = 0;
		if ((low & 0x8000) != 0) {
			low = low & 0x7FFF;
			high = buf.readUnsignedByte();
		}
		return ((high & 0xFF) << 15) | low;
	}

	private void writeExtendedForgeShort(ByteBuf buf, int toWrite) {
		int low = toWrite & 0x7FFF;
		int high = (toWrite & 0x7F8000) << 15;
		if (high != 0) {
			low = low | 0x8000;
		}
		buf.writeShort(low);
		if (high != 0) {
			buf.writeByte(high);
		}
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
