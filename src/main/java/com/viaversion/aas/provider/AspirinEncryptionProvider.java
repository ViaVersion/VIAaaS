package com.viaversion.aas.provider;

import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.providers.EncryptionProvider;

public class AspirinEncryptionProvider extends EncryptionProvider {
    @Override
    public void enableDecryption(UserConnection user) {
        throw new UnsupportedOperationException("todo"); // todo implement this
    }
}
