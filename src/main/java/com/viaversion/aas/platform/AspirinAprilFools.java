package com.viaversion.aas.platform;

import net.raphimc.viaaprilfools.platform.ViaAprilFoolsPlatform;

import java.io.File;
import java.util.logging.Logger;

public class AspirinAprilFools implements ViaAprilFoolsPlatform {
	private final Logger logger = Logger.getLogger("ViaAprilFools");
	@Override
	public Logger getLogger() {
		return logger;
	}

	public void init() {
		init(new File("config/viaaprilfools.yml"));
	}

	@Override
	public File getDataFolder() {
		return new File("config/viaaprilfools");
	}
}
