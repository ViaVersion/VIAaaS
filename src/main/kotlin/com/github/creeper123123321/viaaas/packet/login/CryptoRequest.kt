package com.github.creeper123123321.viaaas.packet.login

import com.github.creeper123123321.viaaas.packet.Packet
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec

class CryptoRequest : Packet {
    lateinit var serverId: String
    lateinit var publicKey: PublicKey
    lateinit var token: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        serverId = Type.STRING.read(byteBuf)
        if (protocolVersion >= ProtocolVersion.v1_8.version) {
            publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)))
            token = Type.BYTE_ARRAY_PRIMITIVE.read(byteBuf)
        } else {
            publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(ByteArray(byteBuf.readUnsignedShort()).also { byteBuf.readBytes(it) }))
            token = ByteArray(byteBuf.readUnsignedShort()).also { byteBuf.readBytes(it) }
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, serverId)
        if (protocolVersion >= ProtocolVersion.v1_8.version) {
            Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, publicKey.encoded)
            Type.BYTE_ARRAY_PRIMITIVE.write(byteBuf, token)
        } else {
            val encodedKey = publicKey.encoded
            byteBuf.writeShort(encodedKey.size)
            byteBuf.writeBytes(encodedKey)
            byteBuf.writeShort(token.size)
            byteBuf.writeBytes(token)
        }
    }
}