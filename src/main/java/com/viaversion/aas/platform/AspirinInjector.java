package com.viaversion.aas.platform;

import com.viaversion.viaversion.api.platform.ViaInjector;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.fastutil.ints.IntLinkedOpenHashSet;
import com.viaversion.viaversion.libs.fastutil.ints.IntSortedSet;
import com.viaversion.viaversion.libs.gson.JsonObject;

public class AspirinInjector implements ViaInjector {
	@Override
	public void inject() {
	}

	@Override
	public void uninject() {
	}

	@Override
	public int getServerProtocolVersion() {
		return getServerProtocolVersions().firstInt();
	}

	@Override
	public String getEncoderName() {
		return getDecoderName();
	}

	@Override
	public String getDecoderName() {
		return "via-codec";
	}

	@Override
	public JsonObject getDump() {
		return new JsonObject();
	}

	@Override
	public IntSortedSet getServerProtocolVersions() {
		var versions = new IntLinkedOpenHashSet();
		versions.add(ProtocolVersion.v1_7_1.getOriginalVersion());
		versions.add(ProtocolVersion.getProtocols()
				.stream()
				.mapToInt(ProtocolVersion::getOriginalVersion)
				.max().orElseThrow());
		return versions;
	}
}
