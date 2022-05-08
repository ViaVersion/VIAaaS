package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.parseUndashedId
import com.viaversion.aas.type.AspirinTypes
import com.viaversion.aas.util.SignableProperty
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf
import java.util.*

class LoginSuccess : Packet {
    lateinit var id: UUID
    lateinit var username: String
    val properties = mutableListOf<SignableProperty>()

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        id = when {
            protocolVersion >= ProtocolVersion.v1_16.version -> {
                Type.UUID_INT_ARRAY.read(byteBuf)
            }
            protocolVersion >= ProtocolVersion.v1_7_6.version || protocolVersion == 1 -> {
                UUID.fromString(Type.STRING.read(byteBuf))
            }
            else -> parseUndashedId(Type.STRING.read(byteBuf))
        }
        username = Type.STRING.read(byteBuf)
        if (protocolVersion >= ProtocolVersion.v1_19.version) {
            properties.addAll(AspirinTypes.SIGNABLE_PROPERTY_ARRAY.read(byteBuf).asList())
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        when {
            protocolVersion >= ProtocolVersion.v1_16.version -> {
                Type.UUID_INT_ARRAY.write(byteBuf, id)
            }
            protocolVersion >= ProtocolVersion.v1_7_6.version || protocolVersion == 1 -> {
                Type.STRING.write(byteBuf, id.toString())
            }
            else -> Type.STRING.write(byteBuf, id.toString().replace("-", ""))
        }
        Type.STRING.write(byteBuf, username)
        if (protocolVersion >= ProtocolVersion.v1_19.version) {
            AspirinTypes.SIGNABLE_PROPERTY_ARRAY.write(byteBuf, properties.toTypedArray())
        }
    }
}