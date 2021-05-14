package com.viaversion.aas.codec.packet.play

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf

class Kick : Packet {
    lateinit var msg: String
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        msg = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, msg)
    }
}