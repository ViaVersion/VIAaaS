package com.github.creeper123123321.viaaas.packet.login

import com.github.creeper123123321.viaaas.packet.Packet
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.type.Type
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