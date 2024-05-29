package com.viaversion.aas.codec.packet

import com.google.common.collect.Range
import com.google.common.collect.RangeMap
import com.google.common.collect.TreeRangeMap
import com.viaversion.aas.codec.packet.configuration.*
import com.viaversion.aas.codec.packet.handshake.Handshake
import com.viaversion.aas.codec.packet.login.*
import com.viaversion.aas.codec.packet.play.*
import com.viaversion.aas.codec.packet.status.StatusPing
import com.viaversion.aas.codec.packet.status.StatusPong
import com.viaversion.aas.codec.packet.status.StatusRequest
import com.viaversion.aas.codec.packet.status.StatusResponse
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
import com.viaversion.viaversion.protocols.protocol1_18to1_17_1.ClientboundPackets1_18
import com.viaversion.viaversion.protocols.protocol1_19_1to1_19.ClientboundPackets1_19_1
import com.viaversion.viaversion.protocols.protocol1_19_1to1_19.ServerboundPackets1_19_1
import com.viaversion.viaversion.protocols.protocol1_19_3to1_19_1.ClientboundPackets1_19_3
import com.viaversion.viaversion.protocols.protocol1_19_4to1_19_3.ClientboundPackets1_19_4
import com.viaversion.viaversion.protocols.protocol1_19to1_18_2.ClientboundPackets1_19
import com.viaversion.viaversion.protocols.protocol1_19to1_18_2.ServerboundPackets1_19
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ClientboundConfigurationPackets1_20_2
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ClientboundPackets1_20_2
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ServerboundConfigurationPackets1_20_2
import com.viaversion.viaversion.protocols.protocol1_20_2to1_20.packet.ServerboundPackets1_20_2
import com.viaversion.viaversion.protocols.protocol1_20_5to1_20_3.packet.ClientboundConfigurationPackets1_20_5
import com.viaversion.viaversion.protocols.protocol1_20_5to1_20_3.packet.ClientboundPackets1_20_5
import com.viaversion.viaversion.protocols.protocol1_20_5to1_20_3.packet.ServerboundConfigurationPackets1_20_5
import com.viaversion.viaversion.protocols.protocol1_20_5to1_20_3.packet.ServerboundPackets1_20_5
import com.viaversion.viaversion.protocols.protocol1_8.ClientboundPackets1_8
import com.viaversion.viaversion.protocols.protocol1_9to1_8.ClientboundPackets1_9
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.util.ReferenceCountUtil
import java.util.function.Supplier

object PacketRegistry {
    // state, direction, packet id, protocol version -> entry
    private val entriesDecoding = hashMapOf<Triple<State, Direction, Int>, RangeMap<ProtocolVersion, DecodingInfo>>()

    // direction, type, protocol version -> entry
    private val entriesEncoding = hashMapOf<Pair<Direction, Class<out Packet>>, RangeMap<ProtocolVersion, EncodingInfo>>()

    init {
        // Obviously stolen from https://github.com/VelocityPowered/Velocity/blob/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/protocol/StateRegistry.java
        register(State.HANDSHAKE, Direction.SERVERBOUND, ::Handshake, Range.all(), 0)

        register(State.LOGIN, Direction.SERVERBOUND, ::LoginStart, Range.all(), 0)
        register(State.LOGIN, Direction.SERVERBOUND, ::CryptoResponse, Range.all(), 1)
        register(State.LOGIN, Direction.SERVERBOUND, ::PluginResponse, Range.atLeast(ProtocolVersion.v1_13), 2)
        register(State.LOGIN, Direction.SERVERBOUND, ::LoginAck, Range.atLeast(ProtocolVersion.v1_20_2), 3)

        register(State.LOGIN, Direction.CLIENTBOUND, ::LoginDisconnect, Range.all(), 0)
        register(State.LOGIN, Direction.CLIENTBOUND, ::CryptoRequest, Range.all(), 1)
        register(State.LOGIN, Direction.CLIENTBOUND, ::LoginSuccess, Range.all(), 2)
        register(State.LOGIN, Direction.CLIENTBOUND, ::SetCompression, Range.atLeast(ProtocolVersion.v1_8), 3)
        register(State.LOGIN, Direction.CLIENTBOUND, ::PluginRequest, Range.atLeast(ProtocolVersion.v1_13), 4)

        register(State.STATUS, Direction.SERVERBOUND, ::StatusRequest, Range.all(), 0)
        register(State.STATUS, Direction.SERVERBOUND, ::StatusPing, Range.all(), 1)
        register(State.STATUS, Direction.CLIENTBOUND, ::StatusResponse, Range.all(), 0)
        register(State.STATUS, Direction.CLIENTBOUND, ::StatusPong, Range.all(), 1)

        register(State.CONFIGURATION, Direction.CLIENTBOUND, ::ConfigurationPluginMessage, mapOf(
            ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ClientboundConfigurationPackets1_20_2.CUSTOM_PAYLOAD.id,
            ProtocolVersion.v1_20_5.singleton to ClientboundConfigurationPackets1_20_5.CUSTOM_PAYLOAD.id
        ))
        register(State.CONFIGURATION, Direction.CLIENTBOUND, ::ConfigurationDisconnect, mapOf(
            ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ClientboundConfigurationPackets1_20_2.DISCONNECT.id,
            ProtocolVersion.v1_20_5.singleton to ClientboundConfigurationPackets1_20_5.DISCONNECT.id
        ))
        register(State.CONFIGURATION, Direction.CLIENTBOUND, ::FinishConfig, mapOf(
            ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ClientboundConfigurationPackets1_20_2.FINISH_CONFIGURATION.id,
            ProtocolVersion.v1_20_5.singleton to ClientboundConfigurationPackets1_20_5.FINISH_CONFIGURATION.id
        ))
        register(State.CONFIGURATION, Direction.CLIENTBOUND, ::ConfigurationKeepAlive, mapOf(
            ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ClientboundConfigurationPackets1_20_2.KEEP_ALIVE.id,
            ProtocolVersion.v1_20_5.singleton to ClientboundConfigurationPackets1_20_5.KEEP_ALIVE.id
        ))
        register(State.CONFIGURATION, Direction.CLIENTBOUND, ::ConfigurationTransfer, Range.atLeast(ProtocolVersion.v1_20_5),
            ClientboundConfigurationPackets1_20_5.TRANSFER.id)

        register(State.CONFIGURATION, Direction.SERVERBOUND, ::ConfigurationPluginMessage, mapOf(
            ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ServerboundConfigurationPackets1_20_2.CUSTOM_PAYLOAD.id,
            ProtocolVersion.v1_20_5.singleton to ServerboundConfigurationPackets1_20_5.CUSTOM_PAYLOAD.id
        ))
        register(State.CONFIGURATION, Direction.SERVERBOUND, ::FinishConfig, mapOf(
            ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ServerboundConfigurationPackets1_20_2.FINISH_CONFIGURATION.id,
            ProtocolVersion.v1_20_5.singleton to ServerboundConfigurationPackets1_20_5.FINISH_CONFIGURATION.id
        ))
        register(State.CONFIGURATION, Direction.SERVERBOUND, ::ConfigurationKeepAlive, mapOf(
            ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ServerboundConfigurationPackets1_20_2.KEEP_ALIVE.id,
            ProtocolVersion.v1_20_5.singleton to ServerboundConfigurationPackets1_20_5.KEEP_ALIVE.id
        ))

        register(
            State.PLAY, Direction.CLIENTBOUND, ::Kick, mapOf(
                ProtocolVersion.v1_7_2..ProtocolVersion.v1_8 to ClientboundPackets1_8.DISCONNECT.id,
                ProtocolVersion.v1_9..ProtocolVersion.v1_12_2 to ClientboundPackets1_9.DISCONNECT.id,
                ProtocolVersion.v1_13..ProtocolVersion.v1_13_2 to ClientboundPackets1_13.DISCONNECT.id,
                ProtocolVersion.v1_14..ProtocolVersion.v1_14_4 to ClientboundPackets1_14.DISCONNECT.id,
                ProtocolVersion.v1_15..ProtocolVersion.v1_15_2 to ClientboundPackets1_15.DISCONNECT.id,
                ProtocolVersion.v1_16..ProtocolVersion.v1_16_1 to ClientboundPackets1_16.DISCONNECT.id,
                ProtocolVersion.v1_16_2..ProtocolVersion.v1_16_4 to ClientboundPackets1_16_2.DISCONNECT.id,
                ProtocolVersion.v1_17..ProtocolVersion.v1_17_1 to ClientboundPackets1_17.DISCONNECT.id,
                ProtocolVersion.v1_18..ProtocolVersion.v1_18_2 to ClientboundPackets1_18.DISCONNECT.id,
                ProtocolVersion.v1_19.singleton to ClientboundPackets1_19.DISCONNECT.id,
                ProtocolVersion.v1_19_1.singleton to ClientboundPackets1_19_1.DISCONNECT.id,
                ProtocolVersion.v1_19_3.singleton to ClientboundPackets1_19_3.DISCONNECT.id,
                ProtocolVersion.v1_19_4..ProtocolVersion.v1_20 to ClientboundPackets1_19_4.DISCONNECT.id,
                ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ClientboundPackets1_20_2.DISCONNECT.id,
                ProtocolVersion.v1_20_5.singleton to ClientboundPackets1_20_5.DISCONNECT.id
            )
        )
        register(
            State.PLAY, Direction.CLIENTBOUND, ::PluginMessage, mapOf(
                ProtocolVersion.v1_7_2..ProtocolVersion.v1_8 to ClientboundPackets1_8.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_9..ProtocolVersion.v1_12_2 to ClientboundPackets1_9.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_13..ProtocolVersion.v1_13_2 to ClientboundPackets1_13.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_14..ProtocolVersion.v1_14_4 to ClientboundPackets1_14.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_15..ProtocolVersion.v1_15_2 to ClientboundPackets1_15.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_16..ProtocolVersion.v1_16_1 to ClientboundPackets1_16.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_16_2..ProtocolVersion.v1_16_4 to ClientboundPackets1_16_2.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_17..ProtocolVersion.v1_17_1 to ClientboundPackets1_17.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_18..ProtocolVersion.v1_18_2 to ClientboundPackets1_18.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_19.singleton to ClientboundPackets1_19.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_19_1.singleton to ClientboundPackets1_19_1.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_19_3.singleton to ClientboundPackets1_19_3.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_19_4..ProtocolVersion.v1_20 to ClientboundPackets1_19_4.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ClientboundPackets1_20_2.PLUGIN_MESSAGE.id,
                ProtocolVersion.v1_20_5.singleton to ClientboundPackets1_20_5.PLUGIN_MESSAGE.id
            )
        )
        register(
            State.PLAY, Direction.CLIENTBOUND, ::SetPlayCompression,
            ProtocolVersion.v1_8.singleton, ClientboundPackets1_8.SET_COMPRESSION.id
        )
        register(
            State.PLAY, Direction.SERVERBOUND, ::ConfigurationAck, mapOf(
                ProtocolVersion.v1_20_2..ProtocolVersion.v1_20_3 to ServerboundPackets1_20_2.CONFIGURATION_ACKNOWLEDGED.id,
                ProtocolVersion.v1_20_5.singleton to ServerboundPackets1_20_5.CONFIGURATION_ACKNOWLEDGED.id
            )
        )
        // todo update this to latest version
        register(
            State.PLAY, Direction.SERVERBOUND, ::ServerboundChatCommand,
            mapOf(
                ProtocolVersion.v1_19.singleton to ServerboundPackets1_19.CHAT_COMMAND.id,
                ProtocolVersion.v1_19_1.singleton to ServerboundPackets1_19_1.CHAT_COMMAND.id
            )
        )
        register(
            State.PLAY, Direction.SERVERBOUND, ::ServerboundChatMessage,
            mapOf(
                ProtocolVersion.v1_19.singleton to ServerboundPackets1_19.CHAT_MESSAGE.id,
                ProtocolVersion.v1_19_1.singleton to ServerboundPackets1_19_1.CHAT_MESSAGE.id
            )
        )
    }

    operator fun ProtocolVersion.rangeTo(o: ProtocolVersion): Range<ProtocolVersion> {
        return Range.closed(this, o)
    }

    private val ProtocolVersion.singleton get() = Range.singleton(this)

    private inline fun <reified P : Packet> register(
        state: State,
        direction: Direction,
        constructor: Supplier<P>,
        idByProtocol: Map<Range<ProtocolVersion>, Int>,
        klass: Class<P> = P::class.java,
    ) {
        idByProtocol.forEach { (protocolRange, packetId) ->
            entriesDecoding.computeIfAbsent(Triple(state, direction, packetId)) { TreeRangeMap.create() }
                .also { rangeMap ->
                    if (rangeMap.subRangeMap(protocolRange).asMapOfRanges().isNotEmpty())
                        throw IllegalStateException("entry already exists")
                    rangeMap.put(protocolRange, DecodingInfo(constructor))
                }
        }

        val protocolRangeToId = TreeRangeMap.create<ProtocolVersion, Int>()
        idByProtocol.forEach { (range, id) -> protocolRangeToId.put(range, id) }

        entriesEncoding.computeIfAbsent(direction to klass) { TreeRangeMap.create() }.also { rangeMap ->
            idByProtocol.forEach { (protocolRange, packetId) ->
                if (rangeMap.subRangeMap(protocolRange).asMapOfRanges().isNotEmpty())
                    throw IllegalStateException("entry already exists")
                rangeMap.put(protocolRange, EncodingInfo(packetId))
            }
        }
    }

    private inline fun <reified P : Packet> register(
        state: State,
        direction: Direction,
        constructor: Supplier<P>,
        protocol: Range<ProtocolVersion>,
        id: Int
    ) {
        register(constructor = constructor, direction = direction, state = state, idByProtocol = mapOf(protocol to id))
    }

    data class DecodingInfo(val constructor: Supplier<out Packet>)
    data class EncodingInfo(val packetId: Int)

    private fun getPacketConstructor(
        protocolVersion: ProtocolVersion,
        state: State,
        id: Int,
        direction: Direction
    ): Supplier<out Packet>? {
        return entriesDecoding[Triple(state, direction, id)]?.get(protocolVersion)?.constructor
    }

    private fun getPacketId(packetClass: Class<out Packet>, protocolVersion: ProtocolVersion, direction: Direction): Int? {
        return entriesEncoding[direction to packetClass]?.get(protocolVersion)?.packetId
    }

    fun decode(byteBuf: ByteBuf, protocolVersion: ProtocolVersion, state: State, direction: Direction): Packet {
        val packetId = Type.VAR_INT.readPrimitive(byteBuf)
        val packet = getPacketConstructor(protocolVersion, state, packetId, direction)?.get()
            ?: UnknownPacket(packetId, ByteBufAllocator.DEFAULT.buffer())
        try {
            packet.decode(byteBuf, protocolVersion)
            return ReferenceCountUtil.retain(packet)
        } catch (e: Exception) {
            throw StacklessException("Failed to decode $packetId $state $direction", e)
        } finally {
            ReferenceCountUtil.release(packet)
        }
    }

    fun encode(packet: Packet, byteBuf: ByteBuf, protocolVersion: ProtocolVersion, direction: Direction) {
        val id = if (packet is UnknownPacket) {
            packet.id
        } else {
            getPacketId(packet.javaClass, protocolVersion, direction)
                ?: throw StacklessException("Failed to get id for " + packet::class.java.simpleName)
        }
        Type.VAR_INT.writePrimitive(byteBuf, id)
        try {
            packet.encode(byteBuf, protocolVersion)
        } catch (e: Exception) {
            throw StacklessException("Failed to encode $id $direction", e)
        }
    }
}
