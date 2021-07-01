package com.viaversion.aas.platform;

import com.viaversion.aas.provider.AspirinVersionProvider;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.platform.ViaPlatformLoader;
import com.viaversion.viaversion.api.protocol.version.VersionProvider;
import com.viaversion.viaversion.protocols.protocol1_9to1_8.providers.MovementTransmitterProvider;
import com.viaversion.viaversion.velocity.providers.VelocityMovementTransmitter;

public class AspirinLoader implements ViaPlatformLoader {
	@Override
	public void load() {
		Via.getManager().getProviders().use(MovementTransmitterProvider.class, new VelocityMovementTransmitter());
		Via.getManager().getProviders().use(VersionProvider.class, new AspirinVersionProvider());
	}

	@Override
	public void unload() {
	}
}
