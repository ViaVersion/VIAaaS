package com.viaversion.aas.packet.login

import com.viaversion.aas.packet.Packet
import io.netty.buffer.ByteBuf
import com.viaversion.viaversion.api.type.Type

class LoginDisconnect : Packet {
    lateinit var msg: String
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        msg = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, msg)
    }
}