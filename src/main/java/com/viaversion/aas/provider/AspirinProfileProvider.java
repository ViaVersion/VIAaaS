package com.viaversion.aas.provider;

import com.viaversion.aas.UtilKt;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.model.GameProfile;
import net.raphimc.vialegacy.protocol.release.r1_7_6_10tor1_8.provider.GameProfileFetcher;

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
