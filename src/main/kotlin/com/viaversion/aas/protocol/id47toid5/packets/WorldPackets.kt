package com.viaversion.aas.protocol.id47toid5.packets

import com.viaversion.aas.protocol.*
import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.aas.protocol.id47toid5.chunks.ChunkPacketTransformer
import com.viaversion.aas.protocol.id47toid5.data.Particle1_8to1_7
import com.viaversion.aas.protocol.id47toid5.storage.MapStorage
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.remapper.PacketRemapper
import com.viaversion.viaversion.api.protocol.remapper.TypeRemapper
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.libs.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import com.viaversion.viaversion.libs.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import com.viaversion.viaversion.protocols.protocol1_9to1_8.Protocol1_9To1_8
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Types1_7_6_10
import kotlin.experimental.and

fun Protocol1_8To1_7_6.registerWorldPackets() {
    //Chunk Data
    this.registerClientbound(State.PLAY, 0x21, 0x21, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper -> ChunkPacketTransformer.transformChunk(packetWrapper) }
        }
    })

    //Multi Block Change
    this.registerClientbound(State.PLAY, 0x22, 0x22, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper -> ChunkPacketTransformer.transformMultiBlockChange(packetWrapper) }
        }
    })

    //Block Change
    this.registerClientbound(State.PLAY, 0x23, 0x23, object : PacketRemapper() {
        override fun registerMap() {
            map(xyzUBytePos, TypeRemapper(Type.POSITION)) //Position
            handler { packetWrapper ->
                val blockId = packetWrapper.read(Type.VAR_INT)
                val meta = packetWrapper.read(Type.UNSIGNED_BYTE).toInt()
                packetWrapper.write(Type.VAR_INT, blockId.shl(4).or(meta and 15))
            } //Block Data
        }
    })

    //Block Action
    this.registerClientbound(State.PLAY, 0x24, 0x24, object : PacketRemapper() {
        override fun registerMap() {
            map(xyzShortPos, TypeRemapper(Type.POSITION)) //Position
            map(Type.UNSIGNED_BYTE)
            map(Type.UNSIGNED_BYTE)
            map(Type.VAR_INT)
        }
    })

    //Block Break Animation
    this.registerClientbound(State.PLAY, 0x25, 0x25, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.VAR_INT) //Entity Id
            map(xyzToPosition, TypeRemapper(Type.POSITION)) //Position
            map(Type.BYTE) //Progress
        }
    })

    //Map Chunk Bulk
    this.registerClientbound(State.PLAY, 0x26, 0x26, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper -> ChunkPacketTransformer.transformChunkBulk(packetWrapper) }
        }
    })

    //Effect
    this.registerClientbound(State.PLAY, 0x28, 0x28, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT) // id
            map(xyzUBytePos, TypeRemapper(Type.POSITION))
            map(Type.INT) // data
            map(Type.BOOLEAN) // relative volume
            handler { packetWrapper ->
                if (packetWrapper.get(Type.INT, 0) == 2006) { // id
                    packetWrapper.cancel()
                }
            }
        }
    })

    //Particle
    this.registerClientbound(State.PLAY, 0x2A, 0x2A, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val parts = packetWrapper.read(Type.STRING).split("_").toTypedArray()
                var particle = Particle1_8to1_7.find(parts[0])
                if (particle == null) particle = Particle1_8to1_7.CRIT
                packetWrapper.write(Type.INT, particle.ordinal)
                packetWrapper.write(Type.BOOLEAN, false)
                packetWrapper.passthrough(Type.FLOAT)
                packetWrapper.passthrough(Type.FLOAT)
                packetWrapper.passthrough(Type.FLOAT)
                packetWrapper.passthrough(Type.FLOAT)
                packetWrapper.passthrough(Type.FLOAT)
                packetWrapper.passthrough(Type.FLOAT)
                packetWrapper.passthrough(Type.FLOAT)
                packetWrapper.passthrough(Type.INT)
                var i = 0
                while (i < particle.extra) {
                    var toWrite = 0
                    if (parts.size - 1 > i) {
                        try {
                            toWrite = parts[i + 1].toInt()
                            if (particle.extra == 1 && parts.size == 3) {
                                ++i
                                toWrite = toWrite or (parts[i + 1].toInt() shl 12)
                            }
                        } catch (ignored: NumberFormatException) {
                        }
                    }
                    packetWrapper.write(Type.VAR_INT, toWrite)
                    ++i
                }
            }
        }
    })

    //Update Sign
    this.registerClientbound(State.PLAY, 0x33, 0x33, object : PacketRemapper() {
        override fun registerMap() {
            map(xyzShortPos, TypeRemapper(Type.POSITION)) //Position
            handler { packetWrapper ->
                for (i in 0..3) {
                    packetWrapper.write(
                        Type.STRING,
                        Protocol1_9To1_8.fixJson(packetWrapper.read(Type.STRING)).toString()
                    )
                }
            }
        }
    })

    //Update Block Entity
    this.registerClientbound(State.PLAY, 0x35, 0x35, object : PacketRemapper() {
        override fun registerMap() {
            map(xyzShortPos, TypeRemapper(Type.POSITION)) //Position
            map(Type.UNSIGNED_BYTE) //Action
            map(Types1_7_6_10.COMPRESSED_NBT, Type.NBT)
        }
    })

    //Map
    this.registerClientbound(State.PLAY, 0x34, 0x34, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.VAR_INT)
            handler { packetWrapper ->
                val id = packetWrapper.get(Type.VAR_INT, 0)
                val data = packetWrapper.read(Type.SHORT_BYTE_ARRAY)
                val mapStorage = packetWrapper.user().get(MapStorage::class.java)!!
                var mapData = mapStorage.getMapData(id)
                if (mapData == null) mapStorage.putMapData(id, MapStorage.MapData().also { mapData = it })
                if (data[0] == 1.toByte()) {
                    val count = (data.size - 1) / 3
                    mapData!!.mapIcons = Array(count) { i ->
                        MapStorage.MapIcon(
                            (data[i * 3 + 1].toInt() shr 4).toByte(),
                            (data[i * 3 + 1] and 0xF),
                            data[i * 3 + 2],
                            data[i * 3 + 3]
                        )
                    }
                } else if (data[0] == 2.toByte()) {
                    mapData!!.scale = data[1]
                }
                packetWrapper.write(Type.BYTE, mapData!!.scale)
                packetWrapper.write(Type.VAR_INT, mapData!!.mapIcons.size)
                for (mapIcon in mapData!!.mapIcons) {
                    packetWrapper.write(
                        Type.BYTE,
                        (mapIcon.direction.toInt().shl(4).or(mapIcon.type.toInt() and 0xF)).toByte()
                    )
                    packetWrapper.write(Type.BYTE, mapIcon.x)
                    packetWrapper.write(Type.BYTE, mapIcon.z)
                }
                if (data[0] == 0.toByte()) {
                    val x = data[1]
                    val z = data[2]
                    val rows = data.size - 3
                    packetWrapper.write(Type.BYTE, 1.toByte())
                    packetWrapper.write(Type.BYTE, rows.toByte())
                    packetWrapper.write(Type.BYTE, x)
                    packetWrapper.write(Type.BYTE, z)
                    val newData = ByteArray(rows)
                    for (i in 0 until rows) {
                        newData[i] = data[i + 3]
                    }
                    packetWrapper.write(Type.BYTE_ARRAY_PRIMITIVE, newData)
                } else {
                    packetWrapper.write(Type.BYTE, 0.toByte())
                }
            }
        }
    })

    //Open Sign Editor
    this.registerClientbound(State.PLAY, 0x36, 0x36, object : PacketRemapper() {
        override fun registerMap() {
            map(xyzToPosition, TypeRemapper(Type.POSITION)) //Position
        }
    })


    //Player Digging
    this.registerServerbound(State.PLAY, 0x07, 0x07, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.UNSIGNED_BYTE, Type.BYTE) //Status
            map(TypeRemapper(Type.POSITION), xyzUBytePosWriter)
            map(Type.BYTE) //Face
        }
    })

    //Player Block Placement
    this.registerServerbound(State.PLAY, 0x08, 0x08, object : PacketRemapper() {
        override fun registerMap() {
            map(TypeRemapper(Type.POSITION), xyzUBytePosWriter)
            map(Type.UNSIGNED_BYTE)
            map(Type.ITEM, Types1_7_6_10.COMPRESSED_NBT_ITEM)
            handler { packetWrapper ->
                val x = packetWrapper.get(Type.INT, 0)
                val y = packetWrapper.get(Type.UNSIGNED_BYTE, 0)
                val z = packetWrapper.get(Type.INT, 1)
                // https://github.com/ViaVersion/ViaVersion/pull/1379
                val direction = packetWrapper.get(Type.UNSIGNED_BYTE, 0) //Direction
                val item = packetWrapper.get(Types1_7_6_10.COMPRESSED_NBT_ITEM, 0)
                if (isPlayerInsideBlock(
                        x.toLong(),
                        y.toLong(),
                        z.toLong(),
                        direction
                    ) && !isPlaceable(item.identifier())
                ) packetWrapper.cancel()
                for (i in 0..2) {
                    if (packetWrapper.isReadable(Type.BYTE, 0)) {
                        packetWrapper.passthrough(Type.BYTE)
                    } else {
                        val cursor = packetWrapper.read(Type.UNSIGNED_BYTE)
                        packetWrapper.write(Type.BYTE, cursor.toByte())
                    }
                }
            }
        }
    })

    //Update Sign
    this.registerServerbound(State.PLAY, 0x12, 0x12, object : PacketRemapper() {
        override fun registerMap() {
            map(TypeRemapper(Type.POSITION), xyzShortPosWriter)
            handler { packetWrapper ->
                for (i in 0..3)
                    packetWrapper.write(
                        Type.STRING, LegacyComponentSerializer.legacySection()
                            .serialize(GsonComponentSerializer.gson().deserialize(packetWrapper.read(Type.STRING)))
                    )
            }
        }
    })
}
