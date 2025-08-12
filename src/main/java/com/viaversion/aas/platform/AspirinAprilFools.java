package com.viaversion.aas.platform;

import com.viaversion.viaaprilfools.platform.ViaAprilFoolsPlatform;
import com.viaversion.viaversion.api.Via;

import java.io.File;
import java.util.logging.Logger;

public class AspirinAprilFools implements ViaAprilFoolsPlatform {
	private final Logger logger = Logger.getLogger("ViaAprilFools");
	@Override
	public Logger getLogger() {
		return logger;
	}

	public void init() {
		init(new File(getDataFolder(), "viaaprilfools.yml"));
	}

	@Override
	public File getDataFolder() {
		return Via.getPlatform().getDataFolder();
	}
}
