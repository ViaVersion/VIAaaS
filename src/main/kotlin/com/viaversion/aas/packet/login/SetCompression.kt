package com.viaversion.aas.packet.login

import com.viaversion.aas.packet.Packet
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf
import kotlin.properties.Delegates

class SetCompression : Packet {
    var threshold by Delegates.notNull<Int>()
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        threshold = Type.VAR_INT.readPrimitive(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, threshold)
    }
}