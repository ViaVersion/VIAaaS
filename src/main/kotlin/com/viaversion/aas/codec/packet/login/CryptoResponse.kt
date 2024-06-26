package com.viaversion.aas.codec.packet.login

import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.protocol.sharewareVersion
import com.viaversion.aas.readByteArray
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Types
import io.netty.buffer.ByteBuf

class CryptoResponse : Packet {
    lateinit var encryptedKey: ByteArray
    var encryptedNonce: ByteArray? = null
    var salt: Long? = null
    var signature: ByteArray? = null

    override fun decode(byteBuf: ByteBuf, protocolVersion: ProtocolVersion) {
        when {
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)
                    && protocolVersion.olderThan(ProtocolVersion.v1_19_3) -> {
                encryptedKey = Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                if (byteBuf.readBoolean()) {
                    encryptedNonce = Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                } else {
                    salt = byteBuf.readLong()
                    signature = Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                }
            }

            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_8) || protocolVersion.equalTo(sharewareVersion) -> {
                encryptedKey = Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
                encryptedNonce = Types.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
            }

            else -> {
                encryptedKey = byteBuf.readByteArray(byteBuf.readUnsignedShort())
                encryptedNonce = byteBuf.readByteArray(byteBuf.readUnsignedShort())
            }
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: ProtocolVersion) {
        when {
            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_19)
                    && protocolVersion.olderThan(ProtocolVersion.v1_19_3) -> {
                Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedKey)
                if (encryptedNonce != null) {
                    byteBuf.writeBoolean(true)
                    Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedNonce)
                } else {
                    byteBuf.writeBoolean(false)
                    byteBuf.writeLong(salt!!)
                    Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, signature)
                }
            }

            protocolVersion.newerThanOrEqualTo(ProtocolVersion.v1_8) || protocolVersion.equalTo(sharewareVersion) -> {
                Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedKey)
                Types.BYTE_ARRAY_PRIMITIVE.write(byteBuf, encryptedNonce)
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