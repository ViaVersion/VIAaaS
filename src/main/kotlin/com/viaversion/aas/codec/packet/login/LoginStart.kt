package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.types.StringType
import io.netty.buffer.ByteBuf

class LoginStart : Packet {
    lateinit var username: String

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        username = StringType(16).read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, username)
    }
}