package com.viaversion.aas.platform;

import com.viaversion.aas.provider.*;
import com.viaversion.viabackwards.protocol.protocol1_20_3to1_20_5.provider.TransferProvider;
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

		//ViaBackwards
		Via.getManager().getProviders().use(TransferProvider.class, new AspirinTransferProvider());

		//ViaLegacy
		Via.getManager().getProviders().use(GameProfileFetcher.class, new AspirinProfileProvider());
		Via.getManager().getProviders().use(EncryptionProvider.class, new AspirinEncryptionProvider());
	}

	@Override
	public void unload() {
	}
}
