package com.viaversion.aas.platform;

import com.viaversion.viaversion.platform.NoopInjector;
import com.viaversion.viaversion.platform.ViaCodecHandler;

public class AspirinInjector extends NoopInjector {

    @Override
    public String getEncoderName() {
        return ViaCodecHandler.NAME;
    }

    @Override
    public String getDecoderName() {
        return ViaCodecHandler.NAME;
    }

}
