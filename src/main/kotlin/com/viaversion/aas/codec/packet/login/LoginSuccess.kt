package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.parseUndashedId
import com.viaversion.aas.protocol.sharewareVersion
import com.viaversion.aas.type.AspirinTypes
import com.viaversion.aas.util.SignableProperty
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf
import java.util.*

class LoginSuccess : Packet {
    lateinit var id: UUID
    lateinit var username: String
    private val properties = mutableListOf<SignableProperty>()

    override fun decode(byteBuf: ByteBuf, protocolVersion: ProtocolVersion) {
        id = when {
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_16) -> {
                Type.UUID.read(byteBuf)
            }
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_7_6) || protocolVersion.equalTo(sharewareVersion) -> {
                UUID.fromString(Type.STRING.read(byteBuf))
            }
            else -> parseUndashedId(Type.STRING.read(byteBuf))
        }
        username = Type.STRING.read(byteBuf)
        if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            properties.addAll(AspirinTypes.SIGNABLE_PROPERTY_ARRAY.read(byteBuf).asList())
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: ProtocolVersion) {
        when {
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_16) -> {
                Type.UUID.write(byteBuf, id)
            }
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_7_6) || protocolVersion.equalTo(sharewareVersion) -> {
                Type.STRING.write(byteBuf, id.toString())
            }
            else -> Type.STRING.write(byteBuf, id.toString().replace("-", ""))
        }
        Type.STRING.write(byteBuf, username)
        if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            AspirinTypes.SIGNABLE_PROPERTY_ARRAY.write(byteBuf, properties.toTypedArray())
        }
    }
}