package com.viaversion.aas.provider;

import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.vialegacy.protocol.release.r1_6_4tor1_7_2_5.provider.EncryptionProvider;

public class AspirinEncryptionProvider extends EncryptionProvider {
    @Override
    public void enableDecryption(UserConnection user) {
        throw new UnsupportedOperationException("todo"); // todo implement this
    }
}
