package com.github.creeper123123321.viaaas.packet.login

import com.github.creeper123123321.viaaas.packet.Packet
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type

class CryptoResponse : Packet {
    lateinit var encryptedKey: ByteArray
    lateinit var encryptedToken: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        if (protocolVersion >= ProtocolVersion.v1_8.version) {
            encryptedKey = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
            encryptedToken = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
        } else {
            encryptedKey = ByteArray(byteBuf.readUnsignedShort()).also { byteBuf.readBytes(it) }
            encryptedToken = ByteArray(byteBuf.readUnsignedShort()).also { byteBuf.readBytes(it) }
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        if (protocolVersion >= ProtocolVersion.v1_8.version) {
            Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedKey)
            Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedToken)
        } else {
            byteBuf.writeShort(encryptedKey.size)
            byteBuf.writeBytes(encryptedKey)
            byteBuf.writeShort(encryptedToken.size)
            byteBuf.writeBytes(encryptedToken)
        }
    }
}