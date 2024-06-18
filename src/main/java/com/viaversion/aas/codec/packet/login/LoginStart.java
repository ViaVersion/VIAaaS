package com.viaversion.aas.codec.packet.login;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.minecraft.ProfileKey;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.type.types.StringType;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class LoginStart implements Packet {
	private String username;
	private ProfileKey profileKey;
	private UUID profileId;

	public ProfileKey getProfileKey() {
		return profileKey;
	}

	public void setProfileKey(ProfileKey profileKey) {
		this.profileKey = profileKey;
	}

	public UUID getProfileId() {
		return profileId;
	}

	public void setProfileId(UUID profileId) {
		this.profileId = profileId;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	@Override
	public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		username = new StringType(16).read(byteBuf);
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)
				&& protocolVersion.olderThan(ProtocolVersion.v1_19_3)) {
			profileKey = Types.OPTIONAL_PROFILE_KEY.read(byteBuf);
		}

		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
			profileId = Types.UUID.read(byteBuf);
		} else if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
			profileId = Types.OPTIONAL_UUID.read(byteBuf);
		}
	}

	@Override
	public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
		Types.STRING.write(byteBuf, username);
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)
				&& protocolVersion.olderThan(ProtocolVersion.v1_19_3)) {
			Types.OPTIONAL_PROFILE_KEY.write(byteBuf, profileKey);
		}
		if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
			Types.UUID.write(byteBuf, profileId);
		} else if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19_1)) {
			Types.OPTIONAL_UUID.write(byteBuf, profileId);
		}
	}
}
