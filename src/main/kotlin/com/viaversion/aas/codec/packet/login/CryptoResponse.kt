package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.readByteArray
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf

class CryptoResponse : Packet {
    lateinit var encryptedKey: ByteArray
    lateinit var encryptedToken: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        if (protocolVersion >= ProtocolVersion.v1_8.version || protocolVersion == 1) {
            encryptedKey = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
            encryptedToken = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
        } else {
            encryptedKey = byteBuf.readByteArray(byteBuf.readUnsignedShort())
            encryptedToken = byteBuf.readByteArray(byteBuf.readUnsignedShort())
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        if (protocolVersion >= ProtocolVersion.v1_8.version || protocolVersion == 1) {
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