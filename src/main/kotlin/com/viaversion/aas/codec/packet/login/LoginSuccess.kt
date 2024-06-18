package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.parseUndashedId
import com.viaversion.aas.protocol.sharewareVersion
import com.viaversion.aas.util.SignableProperty
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Types
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
                Types.UUID.read(byteBuf)
            }
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_7_6) || protocolVersion.equalTo(sharewareVersion) -> {
                UUID.fromString(Types.STRING.read(byteBuf))
            }
            else -> parseUndashedId(Types.STRING.read(byteBuf))
        }
        username = Types.STRING.read(byteBuf)
        if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            val properties = Types.VAR_INT.readPrimitive(byteBuf)
            for (i in 0 until properties) {
                val name = Types.STRING.read(byteBuf)
                val value = Types.STRING.read(byteBuf)
                val signature = Types.OPTIONAL_STRING.read(byteBuf)
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
                Types.UUID.write(byteBuf, id)
            }
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_7_6) || protocolVersion.equalTo(sharewareVersion) -> {
                Types.STRING.write(byteBuf, id.toString())
            }
            else -> Types.STRING.write(byteBuf, id.toString().replace("-", ""))
        }
        Types.STRING.write(byteBuf, username)
        if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            Types.VAR_INT.writePrimitive(byteBuf, properties.size)
            for (property in properties) {
                Types.STRING.write(byteBuf, property.key)
                Types.STRING.write(byteBuf, property.value)
                Types.OPTIONAL_STRING.write(byteBuf, property.signature)
            }
        }
        if (protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            byteBuf.writeBoolean(strictErrorHandling)
        }
    }
}