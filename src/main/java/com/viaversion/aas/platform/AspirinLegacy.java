package com.viaversion.aas.platform;

import com.viaversion.viaversion.api.Via;
import net.raphimc.vialegacy.platform.ViaLegacyPlatform;

import java.io.File;
import java.util.logging.Logger;

public class AspirinLegacy implements ViaLegacyPlatform {
    private final Logger logger = Logger.getLogger("ViaLegacy");
    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public File getDataFolder() {
        return Via.getPlatform().getDataFolder();
    }

    public void init() {
        init(new File(getDataFolder(), "vialegacy.yml"));
    }
}
