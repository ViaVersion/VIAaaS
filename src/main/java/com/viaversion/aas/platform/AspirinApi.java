package com.viaversion.aas.platform;

import com.viaversion.viaversion.ViaAPIBase;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import io.netty.buffer.ByteBuf;

import java.util.UUID;

public class AspirinApi extends ViaAPIBase<UserConnection> {
    @Override
    public ProtocolVersion getPlayerProtocolVersion(UserConnection player) {
        return player.getProtocolInfo().protocolVersion();
    }

    @Override
    public void sendRawPacket(UserConnection player, ByteBuf packet) {
        player.scheduleSendRawPacket(packet);
    }
}
