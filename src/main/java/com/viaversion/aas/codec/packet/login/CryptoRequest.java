package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.UtilKt;
import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.aas.protocol.AspirinProtocolsKt;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public class CryptoRequest implements Packet {
	private String serverId;
	private PublicKey publicKey;
	private byte[] nonce;
	private boolean authenticate;

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		serverId = Types.STRING.read(byteBuf);
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_8)
				|| protocolVersion.equalTo(AspirinProtocolsKt.getSharewareVersion())) {
			publicKey = KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf)));
			nonce = Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf);
		} else {
			publicKey = KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(UtilKt.readByteArray(byteBuf, byteBuf.readUnsignedShort())));
			nonce = UtilKt.readByteArray(byteBuf, byteBuf.readUnsignedShort());
		}
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
			authenticate = byteBuf.readBoolean();
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {

		Types.STRING.write(byteBuf, serverId);
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_8)
				|| protocolVersion.equalTo(AspirinProtocolsKt.getSharewareVersion())) {
			Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, publicKey.getEncoded());
			Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, nonce);
		} else {
			byte[] encodedKey = publicKey.getEncoded();
			byteBuf.writeShort(encodedKey.length);
			byteBuf.writeBytes(encodedKey);
			byteBuf.writeShort(nonce.length);
			byteBuf.writeBytes(nonce);
		}
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
			byteBuf.writeBoolean(authenticate);
		}
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public PublicKey getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(PublicKey publicKey) {
		this.publicKey = publicKey;
	}

	public byte[] getNonce() {
		return nonce;
	}

	public void setNonce(byte[] nonce) {
		this.nonce = nonce;
	}

	public boolean isAuthenticate() {
		return authenticate;
	}

	public void setAuthenticate(boolean authenticate) {
		this.authenticate = authenticate;
	}
}
