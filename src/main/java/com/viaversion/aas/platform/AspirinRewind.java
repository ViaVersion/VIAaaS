package com.viaversion.aas.platform;

import de.gerrygames.viarewind.api.ViaRewindConfigImpl;
import de.gerrygames.viarewind.api.ViaRewindPlatform;

import java.io.File;
import java.util.logging.Logger;

public class AspirinRewind implements ViaRewindPlatform {
	private Logger logger = Logger.getLogger("ViaRewind");
	@Override
	public Logger getLogger() {
		return logger;
	}

	public void init() {
		init(new ViaRewindConfigImpl(new File("config/viarewind.yml")));
	}
}
