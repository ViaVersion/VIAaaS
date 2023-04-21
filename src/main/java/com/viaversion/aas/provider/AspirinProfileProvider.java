package com.viaversion.aas.provider;

import com.viaversion.aas.UtilKt;
import net.raphimc.vialegacy.protocols.release.protocol1_8to1_7_6_10.model.GameProfile;
import net.raphimc.vialegacy.protocols.release.protocol1_8to1_7_6_10.providers.GameProfileFetcher;

import java.util.UUID;

// todo implement an api without blocking
public class AspirinProfileProvider extends GameProfileFetcher {
    @Override
    public UUID loadMojangUUID(String playerName) {
        return UtilKt.generateOfflinePlayerUuid(playerName);
    }

    @Override
    public GameProfile loadGameProfile(UUID uuid) {
        return null;
    }
}
