package com.viaversion.aas.platform;

import com.viaversion.aas.provider.*;
import com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.provider.TransferProvider;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.protocols.v1_8to1_9.provider.CompressionProvider;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.provider.EncryptionProvider;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.provider.GameProfileFetcher;

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
