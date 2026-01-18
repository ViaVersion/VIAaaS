package com.viaversion.aas.platform;

import com.viaversion.viaversion.platform.NoopInjector;

public class AspirinInjector extends NoopInjector {
	@Override
	public String getEncoderName() {
		return getDecoderName();
	}

	@Override
	public String getDecoderName() {
		return "via-codec";
	}

}
