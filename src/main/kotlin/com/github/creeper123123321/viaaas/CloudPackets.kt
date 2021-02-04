package com.github.creeper123123321.viaaas

import com.google.common.collect.Range
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.function.Supplier
import kotlin.properties.Delegates

/**
 * A mutable object which represents a Minecraft packet data
 */
interface Packet {
    fun decode(byteBuf: ByteBuf, protocolVersion: Int)
    fun encode(byteBuf: ByteBuf, protocolVersion: Int)
}

object PacketRegistry {
    val entries = mutableListOf<RegistryEntry>()

    init {
        entries.add(
            RegistryEntry(Range.all(), State.HANDSHAKE, 0, true, ::HandshakePacket, HandshakePacket::class.java)
        )
        entries.add(
            RegistryEntry(Range.all(), State.LOGIN, 0, true, ::LoginStart, LoginStart::class.java)
        )
        entries.add(
            RegistryEntry(Range.all(), State.LOGIN, 1, true, ::CryptoResponse, CryptoResponse::class.java)
        )
        entries.add(
            RegistryEntry(
                Range.atLeast(ProtocolVersion.v1_13.version),
                State.LOGIN,
                2,
                true,
                ::PluginResponse,
                PluginResponse::class.java
            )
        )
        entries.add(
            RegistryEntry(Range.all(), State.LOGIN, 0, false, ::LoginDisconnect, LoginDisconnect::class.java)
        )
        entries.add(
            RegistryEntry(Range.all(), State.LOGIN, 1, false, ::CryptoRequest, CryptoRequest::class.java)
        )
        entries.add(
            RegistryEntry(Range.all(), State.LOGIN, 2, false, ::LoginSuccess, LoginSuccess::class.java)
        )
        entries.add(
            RegistryEntry(Range.all(), State.LOGIN, 3, false, ::SetCompression, SetCompression::class.java)
        )
        entries.add(
            RegistryEntry(Range.all(), State.LOGIN, 4, false, ::PluginRequest, PluginRequest::class.java)
        )
    }

    data class RegistryEntry(
        val versionRange: Range<Int>,
        val state: State,
        val id: Int,
        val serverBound: Boolean,
        val constructor: Supplier<Packet>,
        val packetClass: Class<out Packet>
    )

    fun getPacketConstructor(
        protocolVersion: Int,
        state: State,
        id: Int,
        serverBound: Boolean
    ): Supplier<out Packet>? {
        return entries.firstOrNull {
            it.serverBound == serverBound && it.state == state
                    && it.versionRange.contains(protocolVersion) && it.id == id
        }?.constructor
    }

    fun getPacketId(packetClass: Class<out Packet>, protocolVersion: Int): Int? {
        return entries.firstOrNull {
            it.versionRange.contains(protocolVersion) && it.packetClass == packetClass
        }?.id
    }

    fun decode(byteBuf: ByteBuf, protocolVersion: Int, state: State, serverBound: Boolean): Packet {
        val packetId = Type.VAR_INT.readPrimitive(byteBuf)
        val packet =
            getPacketConstructor(protocolVersion, state, packetId, serverBound)?.get() ?: UnknownPacket(packetId)
        packet.decode(byteBuf, protocolVersion)
        if (byteBuf.isReadable) throw IllegalStateException("Remaining bytes!")
        return packet
    }

    fun encode(packet: Packet, byteBuf: ByteBuf, protocolVersion: Int) {
        val id = if (packet is UnknownPacket) {
            packet.id
        } else {
            getPacketId(packet.javaClass, protocolVersion)!!
        }
        Type.VAR_INT.writePrimitive(byteBuf, id)
        packet.encode(byteBuf, protocolVersion)
    }
}

class UnknownPacket(val id: Int) : Packet {
    lateinit var content: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        content = ByteArray(byteBuf.readableBytes()).also { byteBuf.readBytes(it) }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        byteBuf.writeBytes(content)
    }
}

// Some code based on https://github.com/VelocityPowered/Velocity/tree/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/protocol/packet

class HandshakePacket : Packet {
    var protocolId by Delegates.notNull<Int>()
    lateinit var address: String
    var port by Delegates.notNull<Int>()
    lateinit var nextState: State

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        protocolId = Type.VAR_INT.readPrimitive(byteBuf)
        address = Type.STRING.read(byteBuf)
        port = byteBuf.readUnsignedShort()
        nextState = State.values()[Type.VAR_INT.readPrimitive(byteBuf)]
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, protocolId)
        Type.STRING.write(byteBuf, address)
        byteBuf.writeShort(port)
        byteBuf.writeByte(nextState.ordinal) // var int is too small, fits in a byte
    }
}

class LoginStart : Packet {
    lateinit var username: String

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        username = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, username)
    }
}

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

class PluginResponse : Packet {
    var id by Delegates.notNull<Int>()
    var success by Delegates.notNull<Boolean>()
    lateinit var data: ByteArray
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        id = Type.VAR_INT.readPrimitive(byteBuf)
        success = byteBuf.readBoolean()
        if (success) {
            data = ByteArray(byteBuf.readableBytes()).also { byteBuf.readBytes(it) }
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, id)
        byteBuf.writeBoolean(success)
        if (success) {
            byteBuf.writeBytes(data)
        }
    }
}

class LoginDisconnect : Packet {
    lateinit var msg: String
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        msg = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, msg)
    }
}

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

class LoginSuccess : Packet {
    lateinit var id: UUID
    lateinit var username: String

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        id = when {
            protocolVersion >= ProtocolVersion.v1_16.version -> {
                Type.UUID_INT_ARRAY.read(byteBuf)
            }
            protocolVersion >= ProtocolVersion.v1_7_6.version -> {
                UUID.fromString(Type.STRING.read(byteBuf))
            }
            else -> parseUndashedId(Type.STRING.read(byteBuf))
        }
        username = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        when {
            protocolVersion >= ProtocolVersion.v1_16.version -> {
                Type.UUID_INT_ARRAY.write(byteBuf, id)
            }
            protocolVersion >= ProtocolVersion.v1_7_6.version -> {
                Type.STRING.write(byteBuf, id.toString())
            }
            else -> Type.STRING.write(byteBuf, id.toString().replace("-", ""))
        }
        Type.STRING.write(byteBuf, username)
    }
}

class SetCompression : Packet {
    var threshold by Delegates.notNull<Int>()
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        threshold = Type.VAR_INT.readPrimitive(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, threshold)
    }
}

class PluginRequest : Packet {
    var id by Delegates.notNull<Int>()
    lateinit var channel: String
    lateinit var data: ByteArray
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        id = Type.VAR_INT.readPrimitive(byteBuf)
        channel = Type.STRING.read(byteBuf)
        data = ByteArray(byteBuf.readableBytes()).also { byteBuf.readBytes(it) }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, id)
        Type.STRING.write(byteBuf, channel)
        byteBuf.writeBytes(data)
    }
}