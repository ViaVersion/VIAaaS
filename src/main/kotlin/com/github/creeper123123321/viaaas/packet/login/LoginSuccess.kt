package com.github.creeper123123321.viaaas.packet.login

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.parseUndashedId
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
import java.util.*

class LoginSuccess : Packet {
    lateinit var id: UUID
    lateinit var username: String

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        id = when {
            protocolVersion >= ProtocolVersion.v1_16.version -> {
                Type.UUID_INT_ARRAY.read(byteBuf)
            }
            protocolVersion >= ProtocolVersion.v1_7_6.version -> {
                UUID.fromString(Type.STRING.read(byteBuf))
            }
            else -> parseUndashedId(Type.STRING.read(byteBuf))
        }
        username = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        when {
            protocolVersion >= ProtocolVersion.v1_16.version -> {
                Type.UUID_INT_ARRAY.write(byteBuf, id)
            }
            protocolVersion >= ProtocolVersion.v1_7_6.version -> {
                Type.STRING.write(byteBuf, id.toString())
            }
            else -> Type.STRING.write(byteBuf, id.toString().replace("-", ""))
        }
        Type.STRING.write(byteBuf, username)
    }
}