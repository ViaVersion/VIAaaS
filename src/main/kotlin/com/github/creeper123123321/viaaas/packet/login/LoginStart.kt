package com.github.creeper123123321.viaaas.packet.login

import com.github.creeper123123321.viaaas.packet.Packet
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.type.Type

class LoginStart : Packet {
    lateinit var username: String

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        username = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, username)
    }
}