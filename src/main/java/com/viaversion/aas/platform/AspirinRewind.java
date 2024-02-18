package com.viaversion.aas.platform;

import com.viaversion.viarewind.api.ViaRewindPlatform;
import com.viaversion.viaversion.api.Via;

import java.io.File;
import java.util.logging.Logger;

public class AspirinRewind implements ViaRewindPlatform {
	private final Logger logger = Logger.getLogger("ViaRewind");
	@Override
	public Logger getLogger() {
		return logger;
	}

	public void init() {
		init(new File(getDataFolder(), "viarewind.yml"));
	}

	@Override
	public File getDataFolder() {
		return Via.getPlatform().getDataFolder();
	}
}
