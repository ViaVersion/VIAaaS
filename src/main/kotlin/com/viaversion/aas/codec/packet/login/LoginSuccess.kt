package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.parseUndashedUuid
import com.viaversion.aas.toHexString
import com.viaversion.viaversion.api.minecraft.GameProfile
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Types
import io.netty.buffer.ByteBuf
import java.util.*

class LoginSuccess : Packet {
    lateinit var id: UUID
    lateinit var username: String
    lateinit var properties: Array<GameProfile.Property>
    private var strictErrorHandling: Boolean = false

    override fun decode(byteBuf: ByteBuf, protocolVersion: ProtocolVersion) {
        id = when {
            protocolVersion >= ProtocolVersion.v1_16 -> {
                Types.UUID.read(byteBuf)
            }
            protocolVersion >= ProtocolVersion.v1_7_6 -> {
                UUID.fromString(Types.STRING.read(byteBuf))
            }
            else -> parseUndashedUuid(Types.STRING.read(byteBuf))
        }
        username = Types.STRING.read(byteBuf)
        if (protocolVersion >= ProtocolVersion.v1_19) {
            properties = Types.PROFILE_PROPERTY_ARRAY.read(byteBuf)
        }
        if (protocolVersion >= ProtocolVersion.v1_20_5
            && protocolVersion <= ProtocolVersion.v1_21) {
            strictErrorHandling = byteBuf.readBoolean()
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: ProtocolVersion) {
        when {
            protocolVersion >= ProtocolVersion.v1_16 -> {
                Types.UUID.write(byteBuf, id)
            }
            protocolVersion >= ProtocolVersion.v1_7_6 -> {
                Types.STRING.write(byteBuf, id.toString())
            }
            else -> Types.STRING.write(byteBuf, id.toHexString())
        }
        Types.STRING.write(byteBuf, username)
        if (protocolVersion >= ProtocolVersion.v1_19) {
            Types.PROFILE_PROPERTY_ARRAY.write(byteBuf, properties)
        }
        if (protocolVersion >= ProtocolVersion.v1_20_5
            && protocolVersion <= ProtocolVersion.v1_21) {
            byteBuf.writeBoolean(strictErrorHandling)
        }
    }
}