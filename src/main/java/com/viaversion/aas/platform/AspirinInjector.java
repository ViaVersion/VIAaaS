package com.viaversion.aas.platform;

import com.viaversion.viaversion.api.platform.ViaInjector;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.libs.fastutil.ints.IntLinkedOpenHashSet;
import com.viaversion.viaversion.libs.fastutil.ints.IntSortedSet;
import com.viaversion.viaversion.libs.fastutil.objects.ObjectLinkedOpenHashSet;
import com.viaversion.viaversion.libs.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;

import java.util.SortedSet;

public class AspirinInjector implements ViaInjector {
	@Override
	public void inject() {
	}

	@Override
	public void uninject() {
	}

	@Override
	public ProtocolVersion getServerProtocolVersion() {
		return getServerProtocolVersions().first();
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
	public SortedSet<ProtocolVersion> getServerProtocolVersions() {
		var versions = new ObjectLinkedOpenHashSet<ProtocolVersion>();
		versions.addAll(ProtocolVersion.getProtocols());
		return versions;
	}
}
