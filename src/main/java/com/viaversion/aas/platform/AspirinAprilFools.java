package com.viaversion.aas.platform;

import net.raphimc.viaaprilfools.platform.ViaAprilFoolsPlatform;

import java.io.File;
import java.util.logging.Logger;

public class AspirinAprilFools implements ViaAprilFoolsPlatform {
	private Logger logger = Logger.getLogger("ViaAprilFools");
	@Override
	public Logger getLogger() {
		return logger;
	}

	public void init() {
		init(getDataFolder());
	}

	@Override
	public File getDataFolder() {
		return new File("config/viaaprilfools");
	}
}
