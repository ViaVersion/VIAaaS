package com.viaversion.aas.platform;

import com.viaversion.viaversion.api.Via;
import net.raphimc.vialegacy.platform.ViaLegacyPlatform;

import java.io.File;
import java.util.logging.Logger;

public class AspirinLegacy implements ViaLegacyPlatform {
    @Override
    public Logger getLogger() {
        return Via.getPlatform().getLogger();
    }

    @Override
    public File getDataFolder() {
        return Via.getPlatform().getDataFolder();
    }

    public void init() {
        init(new File(getDataFolder(), "vialegacy.yml"));
    }
}
