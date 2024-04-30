package com.viaversion.aas.codec.packet.handshake;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.aas.util.IntendedState;
import com.viaversion.viaversion.api.protocol.packet.State;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class Handshake implements Packet {
	private int protocolId;
	private String address;
	private int port;
	private State nextState;
	private IntendedState intendedState;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		protocolId = Type.VAR_INT.readPrimitive(byteBuf);
		address = Type.STRING.read(byteBuf);
		port = byteBuf.readUnsignedShort();
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
			intendedState = IntendedState.values()[Type.VAR_INT.readPrimitive(byteBuf) - 1];
		} else {
			nextState = State.values()[Type.VAR_INT.readPrimitive(byteBuf)];
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Type.VAR_INT.writePrimitive(byteBuf, protocolId);
		Type.STRING.write(byteBuf, address);
		byteBuf.writeShort(port);
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
			Type.VAR_INT.writePrimitive(byteBuf, intendedState.ordinal() + 1);
		} else {
			byteBuf.writeByte(nextState.ordinal()); // var int is too small, fits in a byte
		}
	}

	public int getProtocolId() {
		return protocolId;
	}

	public void setProtocolId(int protocolId) {
		this.protocolId = protocolId;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public State getNextState() {
		return nextState;
	}

	public void setNextState(State nextState) {
		this.nextState = nextState;
	}

	public IntendedState getIntendedState() {
		return intendedState;
	}

	public void setIntendedState(IntendedState intendedState) {
		this.intendedState = intendedState;
	}
}
