package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.parseUndashedId
import com.viaversion.aas.protocol.sharewareVersion
import com.viaversion.aas.util.SignableProperty
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf
import java.util.*

class LoginSuccess : Packet {
    lateinit var id: UUID
    lateinit var username: String
    private val properties = mutableListOf<SignableProperty>()
    private var strictErrorHandling: Boolean = false

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
            val properties = Type.VAR_INT.readPrimitive(byteBuf)
            for (i in 0 until properties) {
                val name = Type.STRING.read(byteBuf)
                val value = Type.STRING.read(byteBuf)
                val signature = Type.OPTIONAL_STRING.read(byteBuf)
                this.properties.add(SignableProperty(name, value, signature))
            }
        }
        if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            strictErrorHandling = byteBuf.readBoolean()
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
            Type.VAR_INT.writePrimitive(byteBuf, properties.size)
            for (property in properties) {
                Type.STRING.write(byteBuf, property.key)
                Type.STRING.write(byteBuf, property.value)
                Type.OPTIONAL_STRING.write(byteBuf, property.signature)
            }
        }
        if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            byteBuf.writeBoolean(strictErrorHandling)
        }
    }
}