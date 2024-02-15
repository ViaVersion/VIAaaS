package com.viaversion.aas.codec.packet.play;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.minecraft.PlayerMessageSignature;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class ServerboundChatCommand implements Packet {
	private String message;
	private long timestamp;
	private long salt;
	private ArgumentSignature[] signatures;
	private boolean signedPreview;
	private PlayerMessageSignature[] lastSeenMessages;
	private PlayerMessageSignature lastReceivedMessage;


	public static class ArgumentSignature {
		private String argumentName;
		private byte[] signature;

		public ArgumentSignature(String argumentName, byte[] signature) {
			this.argumentName = argumentName;
			this.signature = signature;
		}

		public String getArgumentName() {
			return argumentName;
		}

		public void setArgumentName(String argumentName) {
			this.argumentName = argumentName;
		}

		public byte[] getSignature() {
			return signature;
		}

		public void setSignature(byte[] signature) {
			this.signature = signature;
		}
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		message = Type.STRING.read(byteBuf);
		timestamp = byteBuf.readLong();
		salt = byteBuf.readLong();
		signatures = new ArgumentSignature[Type.VAR_INT.readPrimitive(byteBuf)];
		for (int i = 0; i < signatures.length; i++) {
			signatures[i] = new ArgumentSignature(Type.STRING.read(byteBuf), Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf));
		}
		signedPreview = byteBuf.readBoolean();
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
			lastSeenMessages = Type.PLAYER_MESSAGE_SIGNATURE_ARRAY.read(byteBuf);
			lastReceivedMessage = Type.OPTIONAL_PLAYER_MESSAGE_SIGNATURE.read(byteBuf);
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Type.STRING.write(byteBuf, message);
		byteBuf.writeLong(timestamp);
		byteBuf.writeLong(salt);
		Type.VAR_INT.writePrimitive(byteBuf, signatures.length);
		for (ArgumentSignature signature : signatures) {
			Type.STRING.write(byteBuf, signature.getArgumentName());
			Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, signature.getSignature());
		}
		byteBuf.writeBoolean(signedPreview);
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
			Type.PLAYER_MESSAGE_SIGNATURE_ARRAY.write(byteBuf, lastSeenMessages);
			Type.OPTIONAL_PLAYER_MESSAGE_SIGNATURE.write(byteBuf, lastReceivedMessage);
		}
	}

	public ArgumentSignature[] getSignatures() {
		return signatures;
	}

	public void setSignatures(ArgumentSignature[] signatures) {
		this.signatures = signatures;
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
