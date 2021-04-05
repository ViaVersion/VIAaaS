package com.viaversion.aas.packet

import com.viaversion.aas.packet.handshake.Handshake
import com.viaversion.aas.packet.login.*
import com.viaversion.aas.packet.play.Kick
import com.viaversion.aas.packet.play.PluginMessage
import com.viaversion.aas.packet.status.StatusPing
import com.viaversion.aas.packet.status.StatusPong
import com.viaversion.aas.packet.status.StatusRequest
import com.viaversion.aas.packet.status.StatusResponse
import com.google.common.collect.Range
import com.viaversion.aas.util.StacklessException
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State
import java.util.function.Supplier

object PacketRegistry {
    val entries = mutableListOf<RegistryEntry>()

    init {
        // Obviosly stolen from https://github.com/VelocityPowered/Velocity/blob/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/protocol/StateRegistry.java
        register(Range.all(), State.HANDSHAKE, 0, true, ::Handshake)
        register(Range.all(), State.LOGIN, 0, true, ::LoginStart)
        register(Range.all(), State.LOGIN, 1, true, ::CryptoResponse)
        register(Range.atLeast(ProtocolVersion.v1_13.version), State.LOGIN, 2, true, ::PluginResponse)
        register(Range.all(), State.LOGIN, 0, false, ::LoginDisconnect)
        register(Range.all(), State.LOGIN, 1, false, ::CryptoRequest)
        register(Range.all(), State.LOGIN, 2, false, ::LoginSuccess)
        register(Range.all(), State.LOGIN, 3, false, ::SetCompression)
        register(Range.atLeast(ProtocolVersion.v1_13.version), State.LOGIN, 4, false, ::PluginRequest)
        register(Range.all(), State.STATUS, 0, true, ::StatusRequest)
        register(Range.all(), State.STATUS, 1, true, ::StatusPing)
        register(Range.all(), State.STATUS, 0, false, ::StatusResponse)
        register(Range.all(), State.STATUS, 1, false, ::StatusPong)
        register(
            ::Kick, State.PLAY, false, mapOf(
                Range.closed(ProtocolVersion.v1_7_1.version, ProtocolVersion.v1_8.version) to 0x40,
                Range.closed(ProtocolVersion.v1_9.version, ProtocolVersion.v1_12_2.version) to 0x1A,
                Range.closed(ProtocolVersion.v1_13.version, ProtocolVersion.v1_13_2.version) to 0x1B,
                Range.closed(ProtocolVersion.v1_14.version, ProtocolVersion.v1_14_4.version) to 0x1A,
                Range.closed(ProtocolVersion.v1_15.version, ProtocolVersion.v1_15_2.version) to 0x1B,
                Range.closed(ProtocolVersion.v1_16.version, ProtocolVersion.v1_16_1.version) to 0x1A,
                Range.closed(ProtocolVersion.v1_16_2.version, ProtocolVersion.v1_16_4.version) to 0x19
            )
        )
        register(
            ::PluginMessage, State.PLAY, true, mapOf(
                Range.closed(ProtocolVersion.v1_7_1.version, ProtocolVersion.v1_8.version) to 0x17,
                Range.closed(ProtocolVersion.v1_9.version, ProtocolVersion.v1_11_1.version) to 0x09,
                Range.singleton(ProtocolVersion.v1_12.version) to 0x0A,
                Range.closed(ProtocolVersion.v1_12_1.version, ProtocolVersion.v1_12_2.version) to 0x09,
                Range.closed(ProtocolVersion.v1_13.version, ProtocolVersion.v1_13_2.version) to 0x0A,
                Range.closed(ProtocolVersion.v1_14.version, ProtocolVersion.v1_16_4.version) to 0x0B
            )
        )
        register(
            ::PluginMessage, State.PLAY, false,
            mapOf(
                Range.closed(ProtocolVersion.v1_7_1.version, ProtocolVersion.v1_8.version) to 0x3F,
                Range.closed(ProtocolVersion.v1_9.version, ProtocolVersion.v1_12_2.version) to 0x18,
                Range.closed(ProtocolVersion.v1_13.version, ProtocolVersion.v1_13_2.version) to 0x19,
                Range.closed(ProtocolVersion.v1_14.version, ProtocolVersion.v1_14_4.version) to 0x18,
                Range.closed(ProtocolVersion.v1_15.version, ProtocolVersion.v1_15_2.version) to 0x19,
                Range.closed(ProtocolVersion.v1_16.version, ProtocolVersion.v1_16_1.version) to 0x18,
                Range.closed(ProtocolVersion.v1_16_2.version, ProtocolVersion.v1_16_4.version) to 0x17
            )
        )
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

    inline fun <reified P : Packet> register(
        constructor: Supplier<P>,
        state: State,
        serverBound: Boolean,
        idByProtocol: Map<Range<Int>, Int>
    ) {
        idByProtocol.forEach { (protocol, id) -> register(protocol, state, id, serverBound, constructor) }
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

    fun getPacketId(packetClass: Class<out Packet>, protocolVersion: Int, serverBound: Boolean): Int? {
        return entries.firstOrNull {
            it.versionRange.contains(protocolVersion) && it.packetClass == packetClass && it.serverBound == serverBound
        }?.id
    }

    fun decode(byteBuf: ByteBuf, protocolVersion: Int, state: State, serverBound: Boolean): Packet {
        val packetId = Type.VAR_INT.readPrimitive(byteBuf)
        val packet =
            getPacketConstructor(protocolVersion, state, packetId, serverBound)?.get() ?: UnknownPacket(packetId)
        packet.decode(byteBuf, protocolVersion)
        if (byteBuf.isReadable) throw StacklessException("Remaining bytes!")
        return packet
    }

    fun encode(packet: Packet, byteBuf: ByteBuf, protocolVersion: Int, serverBound: Boolean) {
        val id = if (packet is UnknownPacket) {
            packet.id
        } else {
            getPacketId(packet.javaClass, protocolVersion, serverBound)!!
        }
        Type.VAR_INT.writePrimitive(byteBuf, id)
        packet.encode(byteBuf, protocolVersion)
    }
}