package com.viaversion.aas.config;

import com.viaversion.viaversion.configuration.AbstractViaConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class AspirinViaConfig extends AbstractViaConfig {
	protected final List<String> UNSUPPORTED = new ArrayList<>();

	public AspirinViaConfig(File file, Logger jLogger) {
		super(file, jLogger);
		UNSUPPORTED.addAll(BUKKIT_ONLY_OPTIONS);
		UNSUPPORTED.addAll(VELOCITY_ONLY_OPTIONS);

		reload();
	}

	@Override
	public List<String> getUnsupportedOptions() {
		return UNSUPPORTED;
	}
}
