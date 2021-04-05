package com.viaversion.aas.packet.handshake

import com.viaversion.aas.packet.Packet
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State
import kotlin.properties.Delegates

class Handshake : Packet {
    var protocolId by Delegates.notNull<Int>()
    lateinit var address: String
    var port by Delegates.notNull<Int>()
    lateinit var nextState: State

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        protocolId = Type.VAR_INT.readPrimitive(byteBuf)
        address = Type.STRING.read(byteBuf)
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