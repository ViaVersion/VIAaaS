package com.viaversion.aas.packet.login

import com.viaversion.aas.packet.Packet
import com.viaversion.aas.readByteArray
import io.netty.buffer.ByteBuf
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type

class CryptoResponse : Packet {
    lateinit var encryptedKey: ByteArray
    lateinit var encryptedToken: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        if (protocolVersion >= ProtocolVersion.v1_8.version) {
            encryptedKey = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
            encryptedToken = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
        } else {
            encryptedKey = byteBuf.readByteArray(byteBuf.readUnsignedShort())
            encryptedToken = byteBuf.readByteArray(byteBuf.readUnsignedShort())
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