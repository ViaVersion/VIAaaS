package com.viaversion.aas.packet

import com.google.common.collect.Range
import com.viaversion.aas.packet.handshake.Handshake
import com.viaversion.aas.packet.login.*
import com.viaversion.aas.packet.play.Kick
import com.viaversion.aas.packet.play.PluginMessage
import com.viaversion.aas.packet.status.StatusPing
import com.viaversion.aas.packet.status.StatusPong
import com.viaversion.aas.packet.status.StatusRequest
import com.viaversion.aas.packet.status.StatusResponse
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.Direction
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.protocols.protocol1_13to1_12_2.ClientboundPackets1_13
import com.viaversion.viaversion.protocols.protocol1_14to1_13_2.ClientboundPackets1_14
import com.viaversion.viaversion.protocols.protocol1_15to1_14_4.ClientboundPackets1_15
import com.viaversion.viaversion.protocols.protocol1_16_2to1_16_1.ClientboundPackets1_16_2
import com.viaversion.viaversion.protocols.protocol1_16to1_15_2.ClientboundPackets1_16
import com.viaversion.viaversion.protocols.protocol1_17to1_16_4.ClientboundPackets1_17
import com.viaversion.viaversion.protocols.protocol1_8.ClientboundPackets1_8
import com.viaversion.viaversion.protocols.protocol1_9to1_8.ClientboundPackets1_9
import io.netty.buffer.ByteBuf
import java.util.function.Supplier

object PacketRegistry {
    val entries = mutableListOf<RegistryEntry>()

    init {
        // Obviously stolen from https://github.com/VelocityPowered/Velocity/blob/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/protocol/StateRegistry.java
        register(State.HANDSHAKE, Direction.SERVERBOUND, ::Handshake, Range.all(), 0)

        register(State.LOGIN, Direction.SERVERBOUND, ::LoginStart, Range.all(), 0)
        register(State.LOGIN, Direction.SERVERBOUND, ::CryptoResponse, Range.all(), 1)
        register(State.LOGIN, Direction.SERVERBOUND, ::PluginResponse, Range.atLeast(ProtocolVersion.v1_13.version), 2)

        register(State.LOGIN, Direction.CLIENTBOUND, ::LoginDisconnect, Range.all(), 0)
        register(State.LOGIN, Direction.CLIENTBOUND, ::CryptoRequest, Range.all(), 1)
        register(State.LOGIN, Direction.CLIENTBOUND, ::LoginSuccess, Range.all(), 2)
        register(State.LOGIN, Direction.CLIENTBOUND, ::SetCompression, Range.all(), 3)
        register(State.LOGIN, Direction.CLIENTBOUND, ::PluginRequest, Range.atLeast(ProtocolVersion.v1_13.version), 4)

        register(State.STATUS, Direction.SERVERBOUND, ::StatusRequest, Range.all(), 0)
        register(State.STATUS, Direction.SERVERBOUND, ::StatusPing, Range.all(), 1)
        register(State.STATUS, Direction.CLIENTBOUND, ::StatusResponse, Range.all(), 0)
        register(State.STATUS, Direction.CLIENTBOUND, ::StatusPong, Range.all(), 1)

        // Play
        register(
            State.PLAY, Direction.CLIENTBOUND, ::Kick, mapOf(
                ProtocolVersion.v1_7_1..ProtocolVersion.v1_8 to ClientboundPackets1_8.DISCONNECT.id,
                ProtocolVersion.v1_9..ProtocolVersion.v1_12_2 to ClientboundPackets1_9.DISCONNECT.id,
                ProtocolVersion.v1_13..ProtocolVersion.v1_13_2 to ClientboundPackets1_13.DISCONNECT.id,
                ProtocolVersion.v1_14..ProtocolVersion.v1_14_4 to ClientboundPackets1_14.DISCONNECT.id,
                ProtocolVersion.v1_15..ProtocolVersion.v1_15_2 to ClientboundPackets1_15.DISCONNECT.id,
                ProtocolVersion.v1_16..ProtocolVersion.v1_16_1 to ClientboundPackets1_16.DISCONNECT.id,
                ProtocolVersion.v1_16_2..ProtocolVersion.v1_16_4 to ClientboundPackets1_16_2.DISCONNECT.id,
                Range.singleton(ProtocolVersion.v1_17.fullSnapshotVersion) to ClientboundPackets1_17.DISCONNECT.id
            )
        )

        register(
            State.PLAY, Direction.CLIENTBOUND, ::PluginMessage, mapOf(
                ProtocolVersion.v1_7_1..ProtocolVersion.v1_8 to ClientboundPackets1_8.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_9..ProtocolVersion.v1_12_2 to ClientboundPackets1_9.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_13..ProtocolVersion.v1_13_2 to ClientboundPackets1_13.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_14..ProtocolVersion.v1_14_4 to ClientboundPackets1_14.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_15..ProtocolVersion.v1_15_2 to ClientboundPackets1_15.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_16..ProtocolVersion.v1_16_1 to ClientboundPackets1_16.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_16_2..ProtocolVersion.v1_16_4 to ClientboundPackets1_16_2.PLUGIN_MESSAGE.id,
                Range.singleton(ProtocolVersion.v1_17.fullSnapshotVersion) to ClientboundPackets1_17.PLUGIN_MESSAGE.id
            )
        )
    }

    operator fun ProtocolVersion.rangeTo(o: ProtocolVersion): Range<Int> {
        return Range.closed(this.originalVersion, o.originalVersion)
    }

    inline fun <reified P : Packet> register(
        state: State,
        direction: Direction,
        constructor: Supplier<P>,
        idByProtocol: Map<Range<Int>, Int>,
        klass: Class<P> = P::class.java,
    ) {
        entries.add(RegistryEntry(idByProtocol, state, direction, constructor, klass))
    }

    inline fun <reified P : Packet> register(
        state: State,
        direction: Direction,
        constructor: Supplier<P>,
        protocol: Range<Int>,
        id: Int
    ) {
        register(constructor = constructor, direction = direction, state = state, idByProtocol = mapOf(protocol to id))
    }

    data class RegistryEntry(
        val idByVersion: Map<Range<Int>, Int>,
        val state: State,
        val direction: Direction,
        val constructor: Supplier<out Packet>,
        val packetClass: Class<out Packet>
    )

    fun getPacketConstructor(
        protocolVersion: Int,
        state: State,
        id: Int,
        direction: Direction
    ): Supplier<out Packet>? {
        return entries.firstOrNull {
            it.direction == direction
                    && it.state == state
                    && it.idByVersion.entries.firstOrNull { it.key.contains(protocolVersion) }?.value == id
        }?.constructor
    }

    fun getPacketId(packetClass: Class<out Packet>, protocolVersion: Int, direction: Direction): Int? {
        return entries.firstOrNull {
            it.packetClass == packetClass && it.direction == direction
        }?.idByVersion?.entries?.firstOrNull { it.key.contains(protocolVersion) }?.value
    }

    fun decode(byteBuf: ByteBuf, protocolVersion: Int, state: State, direction: Direction): Packet {
        val packetId = Type.VAR_INT.readPrimitive(byteBuf)
        val packet =
            getPacketConstructor(protocolVersion, state, packetId, direction)?.get() ?: UnknownPacket(packetId)
        packet.decode(byteBuf, protocolVersion)
        if (byteBuf.isReadable) throw StacklessException("Remaining bytes!")
        return packet
    }

    fun encode(packet: Packet, byteBuf: ByteBuf, protocolVersion: Int, direction: Direction) {
        val id = if (packet is UnknownPacket) {
            packet.id
        } else {
            getPacketId(packet.javaClass, protocolVersion, direction)!!
        }
        Type.VAR_INT.writePrimitive(byteBuf, id)
        packet.encode(byteBuf, protocolVersion)
    }
}