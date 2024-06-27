package com.viaversion.aas.config;

import com.viaversion.aas.platform.AspirinPlatform;
import com.viaversion.viaversion.configuration.AbstractViaConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AspirinViaConfig extends AbstractViaConfig {

	protected final List<String> UNSUPPORTED = new ArrayList<>();

	public AspirinViaConfig() {
		super(new File("config/viaversion.yml"), AspirinPlatform.INSTANCE.getLogger());
		UNSUPPORTED.addAll(BUKKIT_ONLY_OPTIONS);
		UNSUPPORTED.addAll(VELOCITY_ONLY_OPTIONS);

		reload();
	}

	@Override
	protected void handleConfig(Map<String, Object> config) {

	}

	@Override
	public List<String> getUnsupportedOptions() {
		return UNSUPPORTED;
	}
}
