package com.viaversion.aas.platform;

import com.viaversion.aas.provider.AspirinCompressionProvider;
import com.viaversion.aas.provider.AspirinEncryptionProvider;
import com.viaversion.aas.provider.AspirinProfileProvider;
import com.viaversion.aas.provider.AspirinVersionProvider;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.protocols.protocol1_9to1_8.providers.CompressionProvider;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.providers.EncryptionProvider;
import net.raphimc.vialegacy.protocols.release.protocol1_8to1_7_6_10.providers.GameProfileFetcher;

public class AspirinLoader implements ViaPlatformLoader {
	@Override
	public void load() {
		Via.getManager().getProviders().use(VersionProvider.class, new AspirinVersionProvider());
		Via.getManager().getProviders().use(CompressionProvider.class, new AspirinCompressionProvider());

		//ViaLegacy
		Via.getManager().getProviders().use(GameProfileFetcher.class, new AspirinProfileProvider());
		Via.getManager().getProviders().use(EncryptionProvider.class, new AspirinEncryptionProvider());
	}

	@Override
	public void unload() {
	}
}
