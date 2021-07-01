package com.viaversion.aas.config;

import com.viaversion.viaversion.configuration.AbstractViaConfig;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class AspirinViaConfig extends AbstractViaConfig {
	{
		reloadConfig();
	}

	public AspirinViaConfig() {
		super(new File("config/viaversion.yml"));
	}

	@Override
	public URL getDefaultConfigURL() {
		return getClass().getClassLoader().getResource("assets/viaversion/config.yml");
	}

	@Override
	protected void handleConfig(Map<String, Object> config) {

	}

	@Override
	public List<String> getUnsupportedOptions() {
		return List.of(
				"anti-xray-patch", "bungee-ping-interval",
				"bungee-ping-save", "bungee-servers", "quick-move-action-fix", "nms-player-ticking",
				"item-cache", "velocity-ping-interval", "velocity-ping-save", "velocity-servers",
				"blockconnection-method", "change-1_9-hitbox", "change-1_14-hitbox", "block-protocols",
				"block-disconnect-msg", "reload-disconnect-msg", "max-pps", "max-pps-kick-msg", "tracking-period",
				"tracking-warning-pps", "tracking-max-warnings", "tracking-max-kick-msg", "use-new-deathmessages"
		);
	}
}
