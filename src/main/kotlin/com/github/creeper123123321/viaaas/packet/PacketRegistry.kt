package com.github.creeper123123321.viaaas.packet

import com.github.creeper123123321.viaaas.packet.handshake.Handshake
import com.github.creeper123123321.viaaas.packet.login.*
import com.github.creeper123123321.viaaas.packet.status.StatusPing
import com.github.creeper123123321.viaaas.packet.status.StatusPong
import com.github.creeper123123321.viaaas.packet.status.StatusRequest
import com.github.creeper123123321.viaaas.packet.status.StatusResponse
import com.google.common.collect.Range
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State
import java.util.function.Supplier

object PacketRegistry {
    val entries = mutableListOf<RegistryEntry>()

    init {
        register(Range.all(), State.HANDSHAKE, 0, true, ::Handshake)
        register(Range.all(), State.LOGIN, 0, true, ::LoginStart)
        register(Range.all(), State.LOGIN, 1, true, ::CryptoResponse)
        register(Range.atLeast(ProtocolVersion.v1_13.version), State.LOGIN, 2, true, ::PluginResponse)
        register(Range.all(), State.LOGIN, 0, false, ::LoginDisconnect)
        register(Range.all(), State.LOGIN, 1, false, ::CryptoRequest)
        register(Range.all(), State.LOGIN, 2, false, ::LoginSuccess)
        register(Range.all(), State.LOGIN, 3, false, ::SetCompression)
        register(Range.all(), State.LOGIN, 4, false, ::PluginRequest)
        register(Range.all(), State.STATUS, 0, true, ::StatusRequest)
        register(Range.all(), State.STATUS, 1, true, ::StatusPing)
        register(Range.all(), State.STATUS, 0, false, ::StatusResponse)
        register(Range.all(), State.STATUS, 1, false, ::StatusPong)
    }

    inline fun <reified P : Packet> register(
        protocol: Range<Int>,
        state: State,
        id: Int,
        serverBound: Boolean,
        constructor: Supplier<P>
    ) {
        entries.add(RegistryEntry(protocol, state, id, serverBound, constructor, P::class.java))
    }

    data class RegistryEntry(
        val versionRange: Range<Int>,
        val state: State,
        val id: Int,
        val serverBound: Boolean,
        val constructor: Supplier<out Packet>,
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