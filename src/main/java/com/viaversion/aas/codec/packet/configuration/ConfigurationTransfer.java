package com.viaversion.aas.codec.packet.configuration;

import com.viaversion.aas.codec.packet.Packet;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

public class ConfigurationTransfer implements Packet {

    private String host;
    private int port;

    @Override
    public void decode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
        host = Types.STRING.read(byteBuf);
        port = Types.VAR_INT.readPrimitive(byteBuf);
    }

    @Override
    public void encode(@NotNull ByteBuf byteBuf, ProtocolVersion protocolVersion) throws Exception {
        Types.STRING.write(byteBuf, host);
        Types.VAR_INT.writePrimitive(byteBuf, port);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
