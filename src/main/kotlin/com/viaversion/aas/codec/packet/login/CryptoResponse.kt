package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.readByteArray
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf

class CryptoResponse : Packet {
    lateinit var encryptedKey: ByteArray
    var encryptedNonce: ByteArray? = null
    var salt: Long? = null
    var signature: ByteArray? = null

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        when {
            protocolVersion >= ProtocolVersion.v1_19.version -> {
                encryptedKey = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                if (byteBuf.readBoolean()) {
                    encryptedNonce = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                } else {
                    salt = byteBuf.readLong()
                    signature = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                }
            }
            protocolVersion >= ProtocolVersion.v1_8.version || protocolVersion == 1 -> {
                encryptedKey = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                encryptedNonce = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
            }
            else -> {
                encryptedKey = byteBuf.readByteArray(byteBuf.readUnsignedShort())
                encryptedNonce = byteBuf.readByteArray(byteBuf.readUnsignedShort())
            }
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        when {
            protocolVersion >= ProtocolVersion.v1_19.version -> {
                Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedKey)
                if (encryptedNonce != null) {
                    byteBuf.writeBoolean(true)
                    Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedNonce)
                } else {
                    byteBuf.writeBoolean(false)
                    byteBuf.writeLong(salt!!)
                    Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, signature)
                }
            }
            protocolVersion >= ProtocolVersion.v1_8.version || protocolVersion == 1 -> {
                Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedKey)
                Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedNonce)
            }
            else -> {
                byteBuf.writeShort(encryptedKey.size)
                byteBuf.writeBytes(encryptedKey)
                byteBuf.writeShort(encryptedNonce!!.size)
                byteBuf.writeBytes(encryptedNonce)
            }
        }
    }
}