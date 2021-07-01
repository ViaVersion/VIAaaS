package com.viaversion.aas.platform;

import de.gerrygames.viarewind.api.ViaRewindPlatform;

import java.util.logging.Logger;

public class AspirinRewind implements ViaRewindPlatform {
	private Logger logger = Logger.getLogger("ViaRewind");
	@Override
	public Logger getLogger() {
		return logger;
	}
}
