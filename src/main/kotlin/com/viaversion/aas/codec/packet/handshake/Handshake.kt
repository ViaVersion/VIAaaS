package com.viaversion.aas.codec.packet.handshake

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.types.StringType
import io.netty.buffer.ByteBuf
import kotlin.properties.Delegates

class Handshake : Packet {
    var protocolId by Delegates.notNull<Int>()
    lateinit var address: String
    var port by Delegates.notNull<Int>()
    lateinit var nextState: State

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        protocolId = Type.VAR_INT.readPrimitive(byteBuf)
        address = StringType((if (VIAaaSConfig.bungeeCord) Short.MAX_VALUE else 255).toInt()).read(byteBuf)
        port = byteBuf.readUnsignedShort()
        nextState = State.values()[Type.VAR_INT.readPrimitive(byteBuf)]
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, protocolId)
        Type.STRING.write(byteBuf, address)
        byteBuf.writeShort(port)
        byteBuf.writeByte(nextState.ordinal) // var int is too small, fits in a byte
    }
}