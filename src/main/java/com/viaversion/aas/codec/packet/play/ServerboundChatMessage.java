package com.viaversion.aas.codec.packet.play;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.minecraft.PlayerMessageSignature;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class ServerboundChatMessage implements Packet {
	private String message;
	private long timestamp;
	private long salt;
	private byte[] signature;
	private boolean signedPreview;
	private PlayerMessageSignature[] lastSeenMessages;
	private PlayerMessageSignature lastReceivedMessage;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		message = Types.STRING.read(byteBuf);
		timestamp = byteBuf.readLong();
		salt = byteBuf.readLong();
		signature = Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf);
		signedPreview = byteBuf.readBoolean();
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
			lastSeenMessages = Types.PLAYER_MESSAGE_SIGNATURE_ARRAY.read(byteBuf);
			lastReceivedMessage = Types.OPTIONAL_PLAYER_MESSAGE_SIGNATURE.read(byteBuf);
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Types.STRING.write(byteBuf, message);
		byteBuf.writeLong(timestamp);
		byteBuf.writeLong(salt);
		Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, signature);
		byteBuf.writeBoolean(signedPreview);
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
			Types.PLAYER_MESSAGE_SIGNATURE_ARRAY.write(byteBuf, lastSeenMessages);
			Types.OPTIONAL_PLAYER_MESSAGE_SIGNATURE.write(byteBuf, lastReceivedMessage);
		}
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public long getSalt() {
		return salt;
	}

	public void setSalt(long salt) {
		this.salt = salt;
	}

	public byte[] getSignature() {
		return signature;
	}

	public void setSignature(byte[] signature) {
		this.signature = signature;
	}

	public boolean isSignedPreview() {
		return signedPreview;
	}

	public void setSignedPreview(boolean signedPreview) {
		this.signedPreview = signedPreview;
	}

	public PlayerMessageSignature[] getLastSeenMessages() {
		return lastSeenMessages;
	}

	public void setLastSeenMessages(PlayerMessageSignature[] lastSeenMessages) {
		this.lastSeenMessages = lastSeenMessages;
	}

	public PlayerMessageSignature getLastReceivedMessage() {
		return lastReceivedMessage;
	}

	public void setLastReceivedMessage(PlayerMessageSignature lastReceivedMessage) {
		this.lastReceivedMessage = lastReceivedMessage;
	}
}
