package com.viaversion.aas.platform;

import com.viaversion.viarewind.api.ViaRewindPlatform;

import java.io.File;
import java.util.logging.Logger;

public class AspirinRewind implements ViaRewindPlatform {
	private Logger logger = Logger.getLogger("ViaRewind");
	@Override
	public Logger getLogger() {
		return logger;
	}

	public void init() {
		init(new File("config/viarewind.yml"));
	}
}
