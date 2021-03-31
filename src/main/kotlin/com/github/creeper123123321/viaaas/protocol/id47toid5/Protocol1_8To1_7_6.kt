package com.github.creeper123123321.viaaas.protocol.id47toid5

import com.github.creeper123123321.viaaas.protocol.id47toid5.chunks.ChunkPacketTransformer
import com.github.creeper123123321.viaaas.protocol.id47toid5.data.Particle1_8to1_7
import com.github.creeper123123321.viaaas.protocol.id47toid5.metadata.MetadataRewriter
import com.github.creeper123123321.viaaas.protocol.id47toid5.storage.*
import com.github.creeper123123321.viaaas.protocol.id47toid5.type.CustomIntType
import com.google.common.base.Charsets
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.CustomStringType
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Types1_7_6_10
import de.gerrygames.viarewind.utils.ChatUtil
import io.netty.buffer.Unpooled
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.entities.Entity1_10Types
import us.myles.ViaVersion.api.minecraft.Position
import us.myles.ViaVersion.api.minecraft.item.Item
import us.myles.ViaVersion.api.minecraft.metadata.Metadata
import us.myles.ViaVersion.api.protocol.SimpleProtocol
import us.myles.ViaVersion.api.remapper.*
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.api.type.types.CustomByteType
import us.myles.ViaVersion.api.type.types.VoidType
import us.myles.ViaVersion.api.type.types.version.Types1_8
import us.myles.ViaVersion.packets.State
import us.myles.ViaVersion.protocols.protocol1_9to1_8.Protocol1_9To1_8
import us.myles.ViaVersion.util.ChatColorUtil
import us.myles.viaversion.libs.kyori.adventure.text.Component
import us.myles.viaversion.libs.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import us.myles.viaversion.libs.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import us.myles.viaversion.libs.opennbt.tag.builtin.CompoundTag
import us.myles.viaversion.libs.opennbt.tag.builtin.ListTag
import us.myles.viaversion.libs.opennbt.tag.builtin.StringTag
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.and

// Based on https://github.com/Gerrygames/ClientViaVersion
object Protocol1_8To1_7_6 : SimpleProtocol() {
        private val xyzToPosition = ValueReader { packetWrapper: PacketWrapper ->
            val x = packetWrapper.read(Type.INT)
            val y = packetWrapper.read(Type.INT).toShort()
            val z = packetWrapper.read(Type.INT)
            Position(x, y, z)
        }
        private val xyzUBytePos = ValueReader { packetWrapper: PacketWrapper ->
            val x = packetWrapper.read(Type.INT)
            val y = packetWrapper.read(Type.UNSIGNED_BYTE)
            val z = packetWrapper.read(Type.INT)
            Position(x, y, z)
        }
        private val xyzUBytePosWriter: ValueWriter<Position> = ValueWriter<Position> { packetWrapper: PacketWrapper, pos: Position ->
            packetWrapper.write(Type.INT, pos.x.toInt())
            packetWrapper.write(Type.UNSIGNED_BYTE, pos.y.toShort())
            packetWrapper.write(Type.INT, pos.z.toInt())
        }
        private val xyzShortPosWriter: ValueWriter<Position> = ValueWriter<Position> { packetWrapper: PacketWrapper, pos: Position ->
            packetWrapper.write(Type.INT, pos.x.toInt())
            packetWrapper.write(Type.SHORT, pos.y.toShort())
            packetWrapper.write(Type.INT, pos.z.toInt())
        }
        private val xyzShortPos: ValueReader<Position> = ValueReader<Position> { packetWrapper: PacketWrapper ->
            val x = packetWrapper.read(Type.INT)
            val y = packetWrapper.read(Type.SHORT)
            val z = packetWrapper.read(Type.INT)
            Position(x, y, z)
        }
        private val placeable = ArrayList<Int>()

        init {
            placeable.add(6)
            placeable.add(27)
            placeable.add(28)
            placeable.add(30)
            placeable.add(31)
            placeable.add(32)
            placeable.add(37)
            placeable.add(38)
            placeable.add(39)
            placeable.add(40)
            placeable.add(50)
            placeable.add(65)
            placeable.add(66)
            placeable.add(69)
            placeable.add(70)
            placeable.add(72)
            placeable.add(76)
            placeable.add(77)
            placeable.add(96)
            placeable.add(106)
            placeable.add(111)
            placeable.add(131)
            placeable.add(143)
            placeable.add(147)
            placeable.add(148)
            placeable.add(157)
            placeable.add(167)
            placeable.add(175)
            for (i in 256..378) placeable.add(i)
            for (i in 381..396) placeable.add(i)
            for (i in 398..452) placeable.add(i)
            for (i in 2256..2267) placeable.add(i)
        }

    override fun registerPackets() {
        //Keep Alive
        this.registerOutgoing(State.PLAY, 0x00, 0x00, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT)
            }
        })

        //Join Game
        this.registerOutgoing(State.PLAY, 0x01, 0x01, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT) //Entiy Id
                map(Type.UNSIGNED_BYTE) //Gamemode
                map(Type.BYTE) //Dimension
                map(Type.UNSIGNED_BYTE) //Difficulty
                map(Type.UNSIGNED_BYTE) //Max players
                map(Type.STRING) //Level Type
                create { packetWrapper ->
                    packetWrapper.write(Type.BOOLEAN, false) //Reduced Debug Info
                }
            }
        })

        //Chat Message
        this.registerOutgoing(State.PLAY, 0x02, 0x02, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING) //Chat Message
                create { packetWrapper ->
                    packetWrapper.write(Type.BYTE, 0.toByte()) //Position (chat box)
                }
            }
        })

        //Entity Equipment
        this.registerOutgoing(State.PLAY, 0x04, 0x04, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.SHORT) //Slot
                map(Types1_7_6_10.COMPRESSED_NBT_ITEM, Type.ITEM) //Item
            }
        })

        //Spawn Position
        this.registerOutgoing(State.PLAY, 0x05, 0x05, object : PacketRemapper() {
            override fun registerMap() {
                map(xyzToPosition, TypeRemapper(Type.POSITION)) //Position
            }
        })

        //Update Health
        this.registerOutgoing(State.PLAY, 0x06, 0x06, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.FLOAT) //Health
                map(Type.SHORT, Type.VAR_INT) //Food
                map(Type.FLOAT) //Food Saturation
            }
        })

        //Player Position And Look
        this.registerOutgoing(State.PLAY, 0x08, 0x08, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.DOUBLE) //x
                handler { packetWrapper ->
                    val y: Double = packetWrapper.read(Type.DOUBLE)
                    packetWrapper.write(Type.DOUBLE, y - 1.62) //y - fixed value
                }
                map(Type.DOUBLE) //z
                map(Type.FLOAT) //pitch
                map(Type.FLOAT) //yaw
                handler { packetWrapper ->
                    packetWrapper.read(Type.BOOLEAN) //OnGround
                    packetWrapper.write(Type.BYTE, 0.toByte()) //BitMask
                }
            }
        })

        //Use Bed
        this.registerOutgoing(State.PLAY, 0x0A, 0x0A, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(xyzUBytePos, TypeRemapper(Type.POSITION))
            }
        })

        //Animation
        this.registerOutgoing(State.PLAY, 0x0B, 0x0B, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val entityId: Int = packetWrapper.read(Type.VAR_INT) //Entity Id
                    val animation: Short = packetWrapper.read(Type.UNSIGNED_BYTE) //Animation
                    packetWrapper.clearInputBuffer()
                    if (animation.toInt() == 104 || animation.toInt() == 105) {
                        packetWrapper.id = 0x1C //Entity Metadata
                        packetWrapper.write(Type.VAR_INT, entityId) //Entity Id
                        packetWrapper.write(Type.UNSIGNED_BYTE, 0.toShort()) //Index
                        packetWrapper.write(Type.UNSIGNED_BYTE, 0.toShort()) //Type
                        packetWrapper.write(Type.BYTE, (if (animation.toInt() == 104) 0x02 else 0x00).toByte()) //Value (sneaking/not sneaking)
                        packetWrapper.write(Type.UNSIGNED_BYTE, 255.toShort()) //end
                    } else {
                        packetWrapper.write(Type.VAR_INT, entityId) //Entity Id
                        packetWrapper.write(Type.UNSIGNED_BYTE, animation) //Animation
                    }
                }
            }
        })

        //Spawn Player
        this.registerOutgoing(State.PLAY, 0x0C, 0x0C, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val entityId: Int = packetWrapper.passthrough(Type.VAR_INT) //Entity Id
                    val uuid = UUID.fromString(packetWrapper.read(Type.STRING)) //UUID
                    packetWrapper.write(Type.UUID, uuid)
                    val name: String = ChatColorUtil.stripColor(packetWrapper.read(Type.STRING)) //Name
                    val dataCount: Int = packetWrapper.read(Type.VAR_INT) //DataCunt
                    val properties = ArrayList<Tablist.Property>()
                    for (i in 0 until dataCount) {
                        val key: String = packetWrapper.read(Type.STRING) //Name
                        val value: String = packetWrapper.read(Type.STRING) //Value
                        val signature: String = packetWrapper.read(Type.STRING) //Signature
                        properties.add(Tablist.Property(key, value, signature))
                    }
                    val x: Int = packetWrapper.passthrough(Type.INT) //x
                    val y: Int = packetWrapper.passthrough(Type.INT) //y
                    val z: Int = packetWrapper.passthrough(Type.INT) //z
                    val yaw: Byte = packetWrapper.passthrough(Type.BYTE) //yaw
                    val pitch: Byte = packetWrapper.passthrough(Type.BYTE) //pitch
                    val item: Short = packetWrapper.passthrough(Type.SHORT) //Item in hand
                    val metadata = packetWrapper.read(Types1_7_6_10.METADATA_LIST) //Metadata
                    MetadataRewriter.transform(Entity1_10Types.EntityType.PLAYER, metadata)
                    packetWrapper.write<List<Metadata>>(Types1_8.METADATA_LIST, metadata)
                    val tablist = packetWrapper.user().get(Tablist::class.java)!!
                    var entryByName = tablist.getTabListEntry(name)
                    if (entryByName == null && name.length > 14) entryByName = tablist.getTabListEntry(name.substring(0, 14))
                    val entryByUUID = tablist.getTabListEntry(uuid)
                    if (entryByName == null || entryByUUID == null) {
                        if (entryByName != null || entryByUUID != null) {
                            val remove = PacketWrapper(0x38, null, packetWrapper.user())
                            remove.write(Type.VAR_INT, 4)
                            remove.write(Type.VAR_INT, 1)
                            remove.write(Type.UUID, entryByName?.uuid ?: entryByUUID!!.uuid)
                            tablist.remove(entryByName ?: entryByUUID!!)
                            remove.send(Protocol1_8To1_7_6::class.java)
                        }
                        val packetPlayerListItem = PacketWrapper(0x38, null, packetWrapper.user())
                        val newentry = Tablist.TabListEntry(name, uuid)
                        if (entryByName != null || entryByUUID != null) {
                            newentry.displayName = if (entryByUUID != null) entryByUUID.displayName else entryByName!!.displayName
                        }
                        newentry.properties = properties
                        tablist.add(newentry)
                        packetPlayerListItem.write(Type.VAR_INT, 0)
                        packetPlayerListItem.write(Type.VAR_INT, 1)
                        packetPlayerListItem.write(Type.UUID, newentry.uuid)
                        packetPlayerListItem.write(Type.STRING, newentry.name)
                        packetPlayerListItem.write(Type.VAR_INT, dataCount)
                        for (property in newentry.properties) {
                            packetPlayerListItem.write(Type.STRING, property.name)
                            packetPlayerListItem.write(Type.STRING, property.value)
                            packetPlayerListItem.write(Type.BOOLEAN, property.signature != null)
                            if (property.signature != null) packetPlayerListItem.write(Type.STRING, property.signature)
                        }
                        packetPlayerListItem.write(Type.VAR_INT, 0)
                        packetPlayerListItem.write(Type.VAR_INT, 0)
                        packetPlayerListItem.write(Type.BOOLEAN, newentry.displayName != null)
                        if (newentry.displayName != null) {
                            packetPlayerListItem.write(Type.STRING, newentry.displayName)
                        }
                        packetPlayerListItem.send(Protocol1_8To1_7_6::class.java)
                        packetWrapper.cancel()
                        val delayedPacket = PacketWrapper(0x0C, null, packetWrapper.user())
                        delayedPacket.write(Type.VAR_INT, entityId)
                        delayedPacket.write(Type.UUID, uuid)
                        delayedPacket.write(Type.INT, x)
                        delayedPacket.write(Type.INT, y)
                        delayedPacket.write(Type.INT, z)
                        delayedPacket.write(Type.BYTE, yaw)
                        delayedPacket.write(Type.BYTE, pitch)
                        delayedPacket.write(Type.SHORT, item)
                        delayedPacket.write<List<Metadata>>(Types1_8.METADATA_LIST, metadata)
                        Via.getPlatform().runSync({
                            try {
                                delayedPacket.send(Protocol1_8To1_7_6::class.java)
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }, 1L)
                    } else {
                        entryByUUID.properties = properties
                    }
                }
                handler { packetWrapper ->
                    val entityID: Int = packetWrapper.get(Type.VAR_INT, 0)
                    val tracker: EntityTracker = packetWrapper.user().get(EntityTracker::class.java)!!
                    tracker.clientEntityTypes[entityID] = Entity1_10Types.EntityType.PLAYER
                    tracker.sendMetadataBuffer(entityID)
                }
            }
        })

        //Collect Item
        this.registerOutgoing(State.PLAY, 0x0D, 0x0D, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Collected Entity ID
                map(Type.INT, Type.VAR_INT) //Collector Entity ID
            }
        })

        //Spawn Object
        this.registerOutgoing(State.PLAY, 0x0E, 0x0E, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT)
                map(Type.BYTE)
                map(Type.INT)
                map(Type.INT)
                map(Type.INT)
                map(Type.BYTE)
                map(Type.BYTE)
                map(Type.INT)
                handler { packetWrapper ->
                    val type: Byte = packetWrapper.get(Type.BYTE, 0)
                    var x: Int = packetWrapper.get(Type.INT, 0)
                    var y: Int = packetWrapper.get(Type.INT, 1)
                    var z: Int = packetWrapper.get(Type.INT, 2)
                    var yaw: Byte = packetWrapper.get(Type.BYTE, 2)
                    var data: Int = packetWrapper.get(Type.INT, 3)
                    if (type.toInt() == 71) {
                        when (data) {
                            0 -> {
                                z += 32
                                yaw = 0
                            }
                            1 -> {
                                x -= 32
                                yaw = 64 as Byte
                            }
                            2 -> {
                                z -= 32
                                yaw = 128 as Byte
                            }
                            3 -> {
                                x += 32
                                yaw = 192 as Byte
                            }
                        }
                    }
                    if (type.toInt() == 70) {
                        val id = data
                        val metadata = data shr 16
                        data = id or metadata shl 12
                    }
                    if (type.toInt() == 50 || type.toInt() == 70 || type.toInt() == 74) y -= 16
                    packetWrapper.set(Type.INT, 0, x)
                    packetWrapper.set(Type.INT, 1, y)
                    packetWrapper.set(Type.INT, 2, z)
                    packetWrapper.set(Type.BYTE, 2, yaw)
                    packetWrapper.set(Type.INT, 3, data)
                }
                handler { packetWrapper ->
                    val entityID: Int = packetWrapper.get(Type.VAR_INT, 0)
                    val typeID = packetWrapper.get(Type.BYTE, 0).toInt()
                    val tracker = packetWrapper.user().get(EntityTracker::class.java)!!
                    val type: Entity1_10Types.EntityType = Entity1_10Types.getTypeFromId(typeID, true)
                    tracker.clientEntityTypes[entityID] = type
                    tracker.sendMetadataBuffer(entityID)
                }
            }
        })

        //Spawn Mob
        this.registerOutgoing(State.PLAY, 0x0F, 0x0F, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT)
                map(Type.UNSIGNED_BYTE)
                map(Type.INT)
                map(Type.INT)
                map(Type.INT)
                map(Type.BYTE)
                map(Type.BYTE)
                map(Type.BYTE)
                map(Type.SHORT)
                map(Type.SHORT)
                map(Type.SHORT)
                map(Types1_7_6_10.METADATA_LIST, Types1_8.METADATA_LIST)
                handler { packetWrapper ->
                    val entityID: Int = packetWrapper.get(Type.VAR_INT, 0)
                    val typeID = packetWrapper.get(Type.UNSIGNED_BYTE, 0).toInt()
                    val tracker = packetWrapper.user().get(EntityTracker::class.java)!!
                    tracker.clientEntityTypes.put(entityID, Entity1_10Types.getTypeFromId(typeID, false))
                    tracker.sendMetadataBuffer(entityID)
                }
                handler { wrapper ->
                    val metadataList = wrapper.get(Types1_8.METADATA_LIST, 0)
                    val entityID: Int = wrapper.get(Type.VAR_INT, 0)
                    val tracker = wrapper.user().get(EntityTracker::class.java)!!
                    if (tracker.clientEntityTypes.containsKey(entityID)) {
                        MetadataRewriter.transform(tracker.clientEntityTypes[entityID], metadataList)
                    } else {
                        wrapper.cancel()
                    }
                }
            }
        })

        //Spawn Painting
        this.registerOutgoing(State.PLAY, 0x10, 0x10, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT) //Entity Id
                map(Type.STRING) //Title
                map(xyzToPosition, TypeRemapper(Type.POSITION)) //Position
                map(Type.INT, Type.BYTE) //Rotation
                handler { packetWrapper ->
                    val entityID = packetWrapper.get(Type.VAR_INT, 0)
                    val tracker = packetWrapper.user().get(EntityTracker::class.java)!!
                    tracker.clientEntityTypes.put(entityID, Entity1_10Types.EntityType.PAINTING)
                    tracker.sendMetadataBuffer(entityID)
                }
            }
        })

        //Spawn Experience Orb
        this.registerOutgoing(State.PLAY, 0x11, 0x11, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT)
                map(Type.INT)
                map(Type.INT)
                map(Type.INT)
                map(Type.SHORT)
                handler { packetWrapper ->
                    val entityID: Int = packetWrapper.get(Type.VAR_INT, 0)
                    val tracker = packetWrapper.user().get(EntityTracker::class.java)!!
                    tracker.clientEntityTypes.put(entityID, Entity1_10Types.EntityType.EXPERIENCE_ORB)
                    tracker.sendMetadataBuffer(entityID)
                }
            }
        })

        //Entity Velocity
        this.registerOutgoing(State.PLAY, 0x12, 0x12, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.SHORT) //velX
                map(Type.SHORT) //velY
                map(Type.SHORT) //velZ
            }
        })

        //Destroy Entities
        this.registerOutgoing(State.PLAY, 0x13, 0x13, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val amount = packetWrapper.read(Type.BYTE).toInt()
                    val customIntType = CustomIntType(amount)
                    val entityIds = packetWrapper.read(customIntType)
                    packetWrapper.write(Type.VAR_INT_ARRAY_PRIMITIVE, entityIds)
                } //Entity Id Array
                handler { packetWrapper ->
                    val tracker = packetWrapper.user().get(EntityTracker::class.java)!!
                    for (entityId in packetWrapper.get(Type.VAR_INT_ARRAY_PRIMITIVE, 0)) tracker.removeEntity(entityId)
                }
            }
        })

        //Entity
        this.registerOutgoing(State.PLAY, 0x14, 0x14, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
            }
        })

        //Entity Relative Move
        this.registerOutgoing(State.PLAY, 0x15, 0x15, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.BYTE) //x
                map(Type.BYTE) //y
                map(Type.BYTE) //z
                create { packetWrapper ->
                    packetWrapper.write(Type.BOOLEAN, true) //OnGround
                }
            }
        })

        //Entity Look
        this.registerOutgoing(State.PLAY, 0x16, 0x16, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.BYTE) //yaw
                map(Type.BYTE) //pitch
                create { packetWrapper ->
                    packetWrapper.write(Type.BOOLEAN, true) //OnGround
                }
            }
        })

        //Entity Look and Relative Move
        this.registerOutgoing(State.PLAY, 0x17, 0x17, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.BYTE) //x
                map(Type.BYTE) //y
                map(Type.BYTE) //z
                map(Type.BYTE) //yaw
                map(Type.BYTE) //pitch
                create { packetWrapper ->
                    packetWrapper.write(Type.BOOLEAN, true) //OnGround
                }
            }
        })

        //Entity Teleport
        this.registerOutgoing(State.PLAY, 0x18, 0x18, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.INT) //x
                map(Type.INT) //y
                map(Type.INT) //z
                map(Type.BYTE) //yaw
                map(Type.BYTE) //pitch
                create { packetWrapper ->
                    packetWrapper.write(Type.BOOLEAN, true) //OnGround
                }
            }
        })

        //Entity Head Look
        this.registerOutgoing(State.PLAY, 0x19, 0x19, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.BYTE) //Head yaw
            }
        })

        //Entity MetadataType
        this.registerOutgoing(State.PLAY, 0x1C, 0x1C, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Types1_7_6_10.METADATA_LIST, Types1_8.METADATA_LIST) //MetadataType
                handler { wrapper ->
                    val metadataList = wrapper.get(Types1_8.METADATA_LIST, 0)
                    val entityID: Int = wrapper.get(Type.VAR_INT, 0)
                    val tracker = wrapper.user().get(EntityTracker::class.java)!!
                    if (tracker.clientEntityTypes.containsKey(entityID)) {
                        MetadataRewriter.transform(tracker.clientEntityTypes[entityID], metadataList)
                        if (metadataList.isEmpty()) wrapper.cancel()
                    } else {
                        tracker.addMetadataToBuffer(entityID, metadataList)
                        wrapper.cancel()
                    }
                }
            }
        })

        //Entity Effect
        this.registerOutgoing(State.PLAY, 0x1D, 0x1D, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.BYTE) //Effect Id
                map(Type.BYTE) //Amplifier
                map(Type.SHORT, Type.VAR_INT) //Duration
                create { packetWrapper -> packetWrapper.write(Type.BOOLEAN, false) } //Hide Particles
            }
        })

        //Remove Entity Effect
        this.registerOutgoing(State.PLAY, 0x1E, 0x1E, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                map(Type.BYTE) //Effect Id
            }
        })

        //Set Experience
        this.registerOutgoing(State.PLAY, 0x1F, 0x1F, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.FLOAT) //Experience bar
                map(Type.SHORT, Type.VAR_INT) //Level
                map(Type.SHORT, Type.VAR_INT) //Total Experience
            }
        })

        //Entity Properties
        this.registerOutgoing(State.PLAY, 0x20, 0x20, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.INT, Type.VAR_INT) //Entity Id
                handler { packetWrapper ->
                    val amount: Int = packetWrapper.read(Type.INT)
                    packetWrapper.write(Type.INT, amount)
                    for (i in 0 until amount) {
                        packetWrapper.passthrough(Type.STRING)
                        packetWrapper.passthrough(Type.DOUBLE)
                        val modifierlength = packetWrapper.read(Type.SHORT).toInt()
                        packetWrapper.write(Type.VAR_INT, modifierlength)
                        for (j in 0 until modifierlength) {
                            packetWrapper.passthrough(Type.UUID)
                            packetWrapper.passthrough(Type.DOUBLE)
                            packetWrapper.passthrough(Type.BYTE)
                        }
                    }
                }
            }
        })

        //Chunk Data
        this.registerOutgoing(State.PLAY, 0x21, 0x21, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper -> ChunkPacketTransformer.transformChunk(packetWrapper) }
            }
        })

        //Multi Block Change
        this.registerOutgoing(State.PLAY, 0x22, 0x22, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper -> ChunkPacketTransformer.transformMultiBlockChange(packetWrapper) }
            }
        })

        //Block Change
        this.registerOutgoing(State.PLAY, 0x23, 0x23, object : PacketRemapper() {
            override fun registerMap() {
                map(xyzUBytePos, TypeRemapper(Type.POSITION)) //Position
                handler { packetWrapper ->
                    val blockId: Int = packetWrapper.read(Type.VAR_INT)
                    val meta = packetWrapper.read(Type.UNSIGNED_BYTE).toInt()
                    packetWrapper.write(Type.VAR_INT, blockId shl 4 or (meta and 15))
                } //Block Data
            }
        })

        //Block Action
        this.registerOutgoing(State.PLAY, 0x24, 0x24, object : PacketRemapper() {
            override fun registerMap() {
                map(xyzShortPos, TypeRemapper(Type.POSITION)) //Position
                map(Type.UNSIGNED_BYTE)
                map(Type.UNSIGNED_BYTE)
                map(Type.VAR_INT)
            }
        })

        //Block Break Animation
        this.registerOutgoing(State.PLAY, 0x25, 0x25, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT) //Entity Id
                map(xyzToPosition, TypeRemapper(Type.POSITION)) //Position
                map(Type.BYTE) //Progress
            }
        })

        //Map Chunk Bulk
        this.registerOutgoing(State.PLAY, 0x26, 0x26, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper -> ChunkPacketTransformer.transformChunkBulk(packetWrapper) }
            }
        })

        //Effect
        this.registerOutgoing(State.PLAY, 0x28, 0x28, object : PacketRemapper() {
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
        this.registerOutgoing(State.PLAY, 0x2A, 0x2A, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val parts: Array<String> = packetWrapper.read(Type.STRING).split("_").toTypedArray()
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

        //Spawn Global Entity
        this.registerOutgoing(State.PLAY, 0x2C, 0x2C, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT)
                map(Type.BYTE)
                map(Type.INT)
                map(Type.INT)
                map(Type.INT)
                handler { packetWrapper ->
                    val entityID: Int = packetWrapper.get(Type.VAR_INT, 0)
                    val tracker = packetWrapper.user().get(EntityTracker::class.java)!!
                    tracker.clientEntityTypes[entityID] = Entity1_10Types.EntityType.LIGHTNING
                    tracker.sendMetadataBuffer(entityID)
                }
            }
        })

        //Open Window
        this.registerOutgoing(State.PLAY, 0x2D, 0x2D, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val windowId: Short = packetWrapper.read(Type.UNSIGNED_BYTE)
                    packetWrapper.write(Type.UNSIGNED_BYTE, windowId)
                    val windowType: Short = packetWrapper.read(Type.UNSIGNED_BYTE)
                    packetWrapper.user().get(Windows::class.java)!!.types.put(windowId, windowType)
                    packetWrapper.write(Type.STRING, getInventoryString(windowType.toInt())) //Inventory Type
                    var title: String = packetWrapper.read(Type.STRING) //Title
                    val slots: Short = packetWrapper.read(Type.UNSIGNED_BYTE)
                    val useProvidedWindowTitle: Boolean = packetWrapper.read(Type.BOOLEAN) //Use provided window title
                    title = if (useProvidedWindowTitle) {
                        Protocol1_9To1_8.fixJson(title).toString()
                    } else {
                        LegacyComponentSerializer.legacySection().serialize(Component.translatable(title)) // todo
                    }
                    packetWrapper.write(Type.STRING, title) //Window title
                    packetWrapper.write(Type.UNSIGNED_BYTE, slots)
                    if (packetWrapper.get(Type.UNSIGNED_BYTE, 0) == 11.toShort()) packetWrapper.passthrough(Type.INT) //Entity Id
                }
            }
        })

        //Set Slot
        this.registerOutgoing(State.PLAY, 0x2F, 0x2F, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val windowId: Short = packetWrapper.read(Type.BYTE).toShort() //Window Id
                    val windowType: Short = packetWrapper.user().get(Windows::class.java)!!.get(windowId).toShort()
                    packetWrapper.write(Type.BYTE, windowId.toByte())
                    var slot = packetWrapper.read(Type.SHORT).toInt()
                    if (windowType.toInt() == 4 && slot >= 1) slot += 1
                    packetWrapper.write(Type.SHORT, slot.toShort()) //Slot
                }
                map(Types1_7_6_10.COMPRESSED_NBT_ITEM, Type.ITEM) //Item
            }
        })

        //Window Items
        this.registerOutgoing(State.PLAY, 0x30, 0x30, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val windowId: Short = packetWrapper.passthrough(Type.UNSIGNED_BYTE) //Window Id
                    val windowType: Short = packetWrapper.user().get(Windows::class.java)!![windowId]
                    var items = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM_ARRAY)
                    if (windowType.toInt() == 4) {
                        val old = items
                        items = arrayOfNulls(old.size + 1)
                        items[0] = old[0]
                        System.arraycopy(old, 1, items, 2, old.size - 1)
                        items[1] = Item(351, 3.toByte(), 4.toShort(), null)
                    }
                    packetWrapper.write(Type.ITEM_ARRAY, items) //Items
                }
            }
        })

        //Update Sign
        this.registerOutgoing(State.PLAY, 0x33, 0x33, object : PacketRemapper() {
            override fun registerMap() {
                map(xyzShortPos, TypeRemapper(Type.POSITION)) //Position
                handler { packetWrapper ->
                    for (i in 0..3) {
                        packetWrapper.write(Type.STRING, Protocol1_9To1_8.fixJson(packetWrapper.read(Type.STRING)).toString())
                    }
                }
            }
        })

        //Map
        this.registerOutgoing(State.PLAY, 0x34, 0x34, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT)
                handler { packetWrapper ->
                    val id: Int = packetWrapper.get(Type.VAR_INT, 0)
                    val length: Int = packetWrapper.read(Type.SHORT).toInt()
                    val data: ByteArray = packetWrapper.read(CustomByteType(length))
                    val mapStorage = packetWrapper.user().get(MapStorage::class.java)!!
                    var mapData = mapStorage.getMapData(id)
                    if (mapData == null) mapStorage.putMapData(id, MapStorage.MapData().also { mapData = it })
                    if (data[0] == 1.toByte()) {
                        val count = (data.size - 1) / 3
                        mapData!!.mapIcons = Array(count) { i ->
                            MapStorage.MapIcon((data[i * 3 + 1].toInt() shr 4).toByte(), (data[i * 3 + 1] and 0xF), data[i * 3 + 2], data[i * 3 + 3])
                        }
                    } else if (data[0] == 2.toByte()) {
                        mapData!!.scale = data[1]
                    }
                    packetWrapper.write(Type.BYTE, mapData!!.scale)
                    packetWrapper.write(Type.VAR_INT, mapData!!.mapIcons.size)
                    for (mapIcon in mapData!!.mapIcons) {
                        packetWrapper.write(Type.BYTE, (mapIcon.direction.toInt() shl 4 or mapIcon.type.toInt() and 0xF).toByte())
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

        //Update Block Entity
        this.registerOutgoing(State.PLAY, 0x35, 0x35, object : PacketRemapper() {
            override fun registerMap() {
                map(xyzShortPos, TypeRemapper(Type.POSITION)) //Position
                map(Type.UNSIGNED_BYTE) //Action
                map(Types1_7_6_10.COMPRESSED_NBT, Type.NBT)
            }
        })

        //Open Sign Editor
        this.registerOutgoing(State.PLAY, 0x36, 0x36, object : PacketRemapper() {
            override fun registerMap() {
                map(xyzToPosition, TypeRemapper(Type.POSITION)) //Position
            }
        })

        //Player List Item
        this.registerOutgoing(State.PLAY, 0x38, 0x38, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val name: String = packetWrapper.read(Type.STRING)
                    val displayName: String? = null
                    val online: Boolean = packetWrapper.read(Type.BOOLEAN)
                    val ping: Short = packetWrapper.read(Type.SHORT)
                    val tablist: Tablist = packetWrapper.user().get(Tablist::class.java)!!
                    var entry = tablist.getTabListEntry(name)
                    if (!online && entry != null) {
                        packetWrapper.write(Type.VAR_INT, 4)
                        packetWrapper.write(Type.VAR_INT, 1)
                        packetWrapper.write(Type.UUID, entry.uuid)
                        tablist.remove(entry)
                    } else if (online && entry == null) {
                        entry = Tablist.TabListEntry(name, UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)))
                        entry.displayName = displayName
                        tablist.add(entry)
                        packetWrapper.write(Type.VAR_INT, 0) // Add
                        packetWrapper.write(Type.VAR_INT, 1) // Entries
                        packetWrapper.write(Type.UUID, entry.uuid)
                        packetWrapper.write(Type.STRING, entry.name)
                        packetWrapper.write(Type.VAR_INT, entry.properties.size)
                        for (property in entry.properties) {
                            packetWrapper.write(Type.STRING, property.name)
                            packetWrapper.write(Type.STRING, property.value)
                            packetWrapper.write(Type.BOOLEAN, property.signature != null)
                            if (property.signature != null) packetWrapper.write(Type.STRING, property.signature)
                        }
                        packetWrapper.write(Type.VAR_INT, 0)
                        packetWrapper.write(Type.VAR_INT, ping.toInt())
                        packetWrapper.write(Type.BOOLEAN, entry.displayName != null)
                        if (entry.displayName != null) {
                            packetWrapper.write(Type.STRING, entry.displayName)
                        }
                    } else if (online && Tablist.shouldUpdateDisplayName(entry!!.displayName, displayName)) {
                        entry.displayName = displayName
                        packetWrapper.write(Type.VAR_INT, 3)
                        packetWrapper.write(Type.VAR_INT, 1)
                        packetWrapper.write(Type.UUID, entry.uuid)
                        packetWrapper.write(Type.BOOLEAN, entry.displayName != null)
                        if (entry.displayName != null) {
                            packetWrapper.write(Type.STRING, entry.displayName)
                        }
                    } else if (online) {
                        entry!!.ping = ping.toInt()
                        packetWrapper.write(Type.VAR_INT, 2) // Update ping
                        packetWrapper.write(Type.VAR_INT, 1) // Entries
                        packetWrapper.write(Type.UUID, entry.uuid)
                        packetWrapper.write(Type.VAR_INT, ping.toInt())
                    } else {
                        packetWrapper.write(Type.VAR_INT, 0)
                        packetWrapper.write(Type.VAR_INT, 0)
                    }
                }
            }
        })

        //Scoreboard Objective
        this.registerOutgoing(State.PLAY, 0x3B, 0x3B, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val name: String = packetWrapper.passthrough(Type.STRING)
                    val value: String = packetWrapper.read(Type.STRING)
                    val mode: Byte = packetWrapper.read(Type.BYTE)
                    packetWrapper.write(Type.BYTE, mode)
                    if (mode.toInt() == 0 || mode.toInt() == 2) {
                        packetWrapper.write(Type.STRING, value)
                        packetWrapper.write(Type.STRING, "integer")
                    }
                }
            }
        })

        //Update Score
        this.registerOutgoing(State.PLAY, 0x3C, 0x3C, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val name: String = packetWrapper.passthrough(Type.STRING)
                    val mode: Byte = packetWrapper.passthrough(Type.BYTE)
                    if (mode.toInt() != 1) {
                        val objective: String = packetWrapper.passthrough(Type.STRING)
                        packetWrapper.user().get(Scoreboard::class.java)!!.put(name, objective)
                        packetWrapper.write(Type.VAR_INT, packetWrapper.read(Type.INT))
                    } else {
                        val objective: String = packetWrapper.user().get(Scoreboard::class.java)!!.get(name)
                        packetWrapper.write(Type.STRING, objective)
                    }
                }
            }
        })

        //Scoreboard Teams
        this.registerOutgoing(State.PLAY, 0x3E, 0x3E, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING)
                handler { packetWrapper ->
                    val mode: Byte = packetWrapper.read(Type.BYTE)
                    packetWrapper.write(Type.BYTE, mode)
                    if (mode.toInt() == 0 || mode.toInt() == 2) {
                        packetWrapper.passthrough(Type.STRING)
                        packetWrapper.passthrough(Type.STRING)
                        packetWrapper.passthrough(Type.STRING)
                        packetWrapper.passthrough(Type.BYTE)
                        packetWrapper.write(Type.STRING, "always")
                        packetWrapper.write(Type.BYTE, 0.toByte())
                    }
                    if (mode.toInt() == 0 || mode.toInt() == 3 || mode.toInt() == 4) {
                        val count = packetWrapper.read(Type.SHORT).toInt()
                        val type = CustomStringType(count)
                        val entries: Array<String> = packetWrapper.read(type)
                        packetWrapper.write<Array<String>>(Type.STRING_ARRAY, entries)
                    }
                }
            }
        })

        //Custom Payload
        this.registerOutgoing(State.PLAY, 0x3F, 0x3F, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING)
                handler { packetWrapper ->
                    val channel: String = packetWrapper.get(Type.STRING, 0)
                    val length: Short = packetWrapper.read(Type.SHORT)
                    if (channel == "MC|Brand") {
                        val data: ByteArray = packetWrapper.read(CustomByteType(length.toInt()))
                        val brand = String(data, StandardCharsets.UTF_8)
                        packetWrapper.write(Type.STRING, brand)
                    } else if (channel == "MC|AdvCdm") {
                        val type: Byte = packetWrapper.passthrough(Type.BYTE)
                        if (type.toInt() == 0) {
                            packetWrapper.passthrough(Type.INT)
                            packetWrapper.passthrough(Type.INT)
                            packetWrapper.passthrough(Type.INT)
                            packetWrapper.passthrough(Type.STRING)
                            packetWrapper.passthrough(Type.BOOLEAN)
                        } else if (type.toInt() == 1) {
                            packetWrapper.passthrough(Type.INT)
                            packetWrapper.passthrough(Type.STRING)
                            packetWrapper.passthrough(Type.BOOLEAN)
                        }
                        packetWrapper.write(Type.BYTE, 1.toByte())
                    }
                    if (channel.equals("MC|TrList", ignoreCase = true)) {
                        packetWrapper.passthrough(Type.INT) //Window Id
                        val size: Int = packetWrapper.passthrough(Type.UNSIGNED_BYTE).toInt() //Size
                        for (i in 0 until size) {
                            var item: Item = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM)
                            packetWrapper.write(Type.ITEM, item) //Buy Item 1
                            item = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM)
                            packetWrapper.write(Type.ITEM, item) //Buy Item 3
                            val has3Items: Boolean = packetWrapper.passthrough(Type.BOOLEAN)
                            if (has3Items) {
                                item = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM)
                                packetWrapper.write(Type.ITEM, item) //Buy Item 2
                            }
                            packetWrapper.passthrough(Type.BOOLEAN) //Unavailable
                            packetWrapper.write(Type.INT, 0) //Uses
                            packetWrapper.write(Type.INT, 0) //Max Uses
                        }
                    }
                }
            }
        })

        //Keep Alive
        this.registerIncoming(State.PLAY, 0x00, 0x00, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT, Type.INT)
            }
        })

        //Use Entity
        this.registerIncoming(State.PLAY, 0x02, 0x02, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT, Type.INT)
                handler { packetWrapper ->
                    val mode: Int = packetWrapper.read(Type.VAR_INT)
                    if (mode == 2) {
                        packetWrapper.write(Type.BYTE, 0.toByte())
                        packetWrapper.read(Type.FLOAT)
                        packetWrapper.read(Type.FLOAT)
                        packetWrapper.read(Type.FLOAT)
                    } else {
                        packetWrapper.write(Type.BYTE, mode.toByte())
                    }
                }
            }
        })

        //Player Position
        this.registerIncoming(State.PLAY, 0x04, 0x04, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.DOUBLE) //X
                handler { packetWrapper ->
                    val feetY: Double = packetWrapper.passthrough(Type.DOUBLE)
                    packetWrapper.write(Type.DOUBLE, feetY + 1.62) //HeadY
                }
                map(Type.DOUBLE) //Z
                map(Type.BOOLEAN) //OnGround
            }
        })

        //Player Position And Look
        this.registerIncoming(State.PLAY, 0x06, 0x06, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.DOUBLE) //X
                handler { packetWrapper ->
                    val feetY: Double = packetWrapper.passthrough(Type.DOUBLE)
                    packetWrapper.write(Type.DOUBLE, feetY + 1.62) //HeadY
                }
                map(Type.DOUBLE) //Z
                map(Type.FLOAT) //Yaw
                map(Type.FLOAT) //Pitch
                map(Type.BOOLEAN) //OnGround
            }
        })

        //Player Digging
        this.registerIncoming(State.PLAY, 0x07, 0x07, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.UNSIGNED_BYTE, Type.BYTE) //Status
                map(TypeRemapper(Type.POSITION), xyzUBytePosWriter)
                map(Type.BYTE) //Face
            }
        })

        //Player Block Placement
        this.registerIncoming(State.PLAY, 0x08, 0x08, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val pos: Position = packetWrapper.read(Type.POSITION) //Position
                    val x: Int = pos.x
                    val y: Short = pos.y.toShort()
                    val z: Int = pos.z
                    // https://github.com/ViaVersion/ViaVersion/pull/1379
                    packetWrapper.write(Type.INT, x)
                    packetWrapper.write(Type.UNSIGNED_BYTE, y)
                    packetWrapper.write(Type.INT, z)
                    val direction: Byte = packetWrapper.passthrough(Type.BYTE) //Direction
                    val voidType = VoidType()
                    if (packetWrapper.isReadable(voidType, 0)) packetWrapper.read(voidType)
                    val item: Item = packetWrapper.read(Type.ITEM)
                    packetWrapper.write(Types1_7_6_10.COMPRESSED_NBT_ITEM, item)
                    if (isPlayerInsideBlock(x.toLong(), y.toLong(), z.toLong(), direction) && !isPlaceable(item.identifier)) packetWrapper.cancel()
                    for (i in 0..2) {
                        if (packetWrapper.isReadable(Type.BYTE, 0)) {
                            packetWrapper.passthrough(Type.BYTE)
                        } else {
                            val cursor: Short = packetWrapper.read(Type.UNSIGNED_BYTE)
                            packetWrapper.write(Type.BYTE, cursor.toByte())
                        }
                    }
                }
            }
        })

        //Animation
        this.registerIncoming(State.PLAY, 0x0A, 0x0A, object : PacketRemapper() {
            override fun registerMap() {
                create { packetWrapper ->
                    packetWrapper.write(Type.INT, 0) //Entity Id, hopefully 0 is ok
                    packetWrapper.write(Type.BYTE, 1.toByte()) //Animation
                }
            }
        })

        //Entity Action
        this.registerIncoming(State.PLAY, 0x0B, 0x0B, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT, Type.INT) //Entity Id
                handler { packetWrapper -> packetWrapper.write(Type.BYTE, (packetWrapper.read(Type.VAR_INT) + 1).toByte()) } //Action Id
                map(Type.VAR_INT, Type.INT) //Action Paramter
            }
        })

        //Steer Vehicle
        this.registerIncoming(State.PLAY, 0x0C, 0x0C, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.FLOAT) //Sideways
                map(Type.FLOAT) //Forwards
                handler { packetWrapper ->
                    val flags: Short = packetWrapper.read(Type.UNSIGNED_BYTE)
                    packetWrapper.write(Type.BOOLEAN, flags and 1 == 1.toShort()) //Jump
                    packetWrapper.write(Type.BOOLEAN, flags and 2 == 2.toShort()) //Unmount
                }
            }
        })

        //Click Window
        this.registerIncoming(State.PLAY, 0x0E, 0x0E, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val windowId: Short = packetWrapper.read(Type.UNSIGNED_BYTE) //Window Id
                    packetWrapper.write(Type.BYTE, windowId as Byte)
                    val windowType: Short = packetWrapper.user().get(Windows::class.java)!!.get(windowId)
                    var slot: Int = packetWrapper.read(Type.SHORT).toInt()
                    if (windowType.toInt() == 4) {
                        if (slot.toInt() == 1) {
                            packetWrapper.cancel()
                        } else if (slot > 1) {
                            slot -= 1
                        }
                    }
                    packetWrapper.write(Type.SHORT, slot.toShort()) //Slot
                }
                map(Type.BYTE) //Button
                map(Type.SHORT) //Action Number
                map(Type.BYTE) //Mode
                map(Type.ITEM, Types1_7_6_10.COMPRESSED_NBT_ITEM)
            }
        })

        //Creative Inventory Action
        this.registerIncoming(State.PLAY, 0x10, 0x10, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.SHORT) //Slot
                map(Type.ITEM, Types1_7_6_10.COMPRESSED_NBT_ITEM) //Item
            }
        })

        //Update Sign
        this.registerIncoming(State.PLAY, 0x12, 0x12, object : PacketRemapper() {
            override fun registerMap() {
                map(TypeRemapper(Type.POSITION), xyzShortPosWriter)
                handler { packetWrapper ->
                    for (i in 0..3)
                        packetWrapper.write(Type.STRING, LegacyComponentSerializer.legacySection()
                                .serialize(GsonComponentSerializer.gson().deserialize(packetWrapper.read(Type.STRING))))
                }
            }
        })

        //Tab-Complete
        this.registerIncoming(State.PLAY, 0x14, 0x14, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val text: String = packetWrapper.read(Type.STRING)
                    packetWrapper.clearInputBuffer()
                    packetWrapper.write(Type.STRING, text)
                }
            }
        })

        //Client Settings
        this.registerIncoming(State.PLAY, 0x15, 0x15, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING)
                map(Type.BYTE)
                map(Type.BYTE)
                map(Type.BOOLEAN)
                create { packetWrapper -> packetWrapper.write(Type.BYTE, 0.toByte()) }
                handler { packetWrapper ->
                    val flags: Short = packetWrapper.read(Type.UNSIGNED_BYTE)
                    packetWrapper.write(Type.BOOLEAN, flags and 1 == 1.toShort())
                }
            }
        })

        //Custom Payload
        this.registerIncoming(State.PLAY, 0x17, 0x17, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING)
                handler { packetWrapper ->
                    val channel: String = packetWrapper.get(Type.STRING, 0)
                    if (channel.equals("MC|ItemName", ignoreCase = true)) {
                        val name: ByteArray = packetWrapper.read(Type.STRING).toByteArray(Charsets.UTF_8)
                        packetWrapper.write(Type.REMAINING_BYTES, name)
                    } else if (channel.equals("MC|BEdit", ignoreCase = true) || channel.equals("MC|BSign", ignoreCase = true)) {
                        packetWrapper.read(Type.SHORT) //length
                        val book: Item = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM)
                        val tag: CompoundTag? = book.tag
                        if (tag != null && tag.contains("pages")) {
                            val pages = tag.get<ListTag>("pages")
                            if (pages != null) {
                                (0 until pages.size()).forEach { i ->
                                    val page = pages.get<StringTag>(i)
                                    var value: String? = page.getValue()
                                    value = ChatUtil.jsonToLegacy(value)
                                    page.setValue(value)
                                }
                            }
                        }
                        packetWrapper.write(Type.ITEM, book)
                    }
                    packetWrapper.cancel()
                    packetWrapper.id = -1
                    val newPacketBuf = Unpooled.buffer()
                    packetWrapper.writeToBuffer(newPacketBuf)
                    val newWrapper = PacketWrapper(0x17, newPacketBuf, packetWrapper.user())
                    newWrapper.passthrough(Type.STRING)
                    newWrapper.write(Type.SHORT, newPacketBuf.readableBytes().toShort())
                    newWrapper.sendToServer(Protocol1_8To1_7_6::class.java, true, true)
                }
            }
        })

        //Encryption Request
        this.registerOutgoing(State.LOGIN, 0x01, 0x01, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING) //Server ID
                handler { packetWrapper ->
                    val publicKeyLength = packetWrapper.read(Type.SHORT).toInt()
                    packetWrapper.write(Type.VAR_INT, publicKeyLength)
                    packetWrapper.passthrough(CustomByteType(publicKeyLength))
                    val verifyTokenLength = packetWrapper.read(Type.SHORT).toInt()
                    packetWrapper.write(Type.VAR_INT, verifyTokenLength)
                    packetWrapper.passthrough(CustomByteType(verifyTokenLength))
                }
            }
        })

        //Encryption Response
        this.registerIncoming(State.LOGIN, 0x01, 0x01, object : PacketRemapper() {
            override fun registerMap() {
                handler { packetWrapper ->
                    val sharedSecretLength: Int = packetWrapper.read(Type.VAR_INT)
                    packetWrapper.write(Type.SHORT, sharedSecretLength.toShort())
                    packetWrapper.passthrough(CustomByteType(sharedSecretLength))
                    val verifyTokenLength: Int = packetWrapper.read(Type.VAR_INT)
                    packetWrapper.write(Type.SHORT, verifyTokenLength.toShort())
                    packetWrapper.passthrough(CustomByteType(verifyTokenLength))
                }
            }
        })
    }

    override fun init(userConnection: UserConnection) {
        userConnection.put(Tablist(userConnection))
        userConnection.put(Windows(userConnection))
        userConnection.put(Scoreboard(userConnection))
        userConnection.put(EntityTracker(userConnection))
        userConnection.put(MapStorage(userConnection))
    }

    private fun isPlayerInsideBlock(x: Long, y: Long, z: Long, direction: Byte): Boolean {
        //switch (direction) {
        //    case 0: {
        //        y--;
        //        break;
        //    }
        //    case 1: {
        //        y++;
        //        break;
        //    }
        //    case 2: {
        //        z--;
        //        break;
        //    }
        //    case 3: {
        //        z++;
        //        break;
        //    }
        //    case 4: {
        //        x--;
        //        break;
        //    }
        //    case 5: {
        //        x++;
        //        break;
        //    }
        //}
        //return Math.abs(The5zigAPI.getAPI().getPlayerPosX() - (x + 0.5)) < 0.8 && Math.abs(The5zigAPI.getAPI().getPlayerPosZ() - (z + 0.5)) < 0.8 && Math.abs((The5zigAPI.getAPI().getPlayerPosY() + 0.9) - (y + 0.5)) < 1.4;
        return false
    }

    private fun isPlaceable(id: Int): Boolean {
        return placeable.contains(id)
    }

    private fun getInventoryString(b: Int): String {
        return when (b) {
            0 -> "minecraft:chest"
            1 -> "minecraft:crafting_table"
            2 -> "minecraft:furnace"
            3 -> "minecraft:dispenser"
            4 -> "minecraft:enchanting_table"
            5 -> "minecraft:brewing_stand"
            6 -> "minecraft:villager"
            7 -> "minecraft:beacon"
            8 -> "minecraft:anvil"
            9 -> "minecraft:hopper"
            10 -> "minecraft:dropper"
            11 -> "EntityHorse"
            else -> throw IllegalArgumentException("Unknown type $b")
        }
    }

}