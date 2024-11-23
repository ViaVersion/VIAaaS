package com.viaversion.aas.platform;

import com.viaversion.viabackwards.api.ViaBackwardsPlatform;
import com.viaversion.viaversion.api.Via;

import java.io.File;
import java.util.logging.Logger;

public class AspirinBackwards implements ViaBackwardsPlatform {
	private final Logger logger = Logger.getLogger("ViaBackwards");
	@Override
	public Logger getLogger() {
		return logger;
	}

	public void init() {
		init(new File(getDataFolder(), "viabackwards.yml"));
		enable();
	}

	@Override
	public void disable() {
	}

	@Override
	public File getDataFolder() {
		return Via.getPlatform().getDataFolder();
	}
}
