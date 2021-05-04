package com.viaversion.aas.protocol.id47toid5.packets

import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.aas.protocol.id47toid5.metadata.MetadataRewriter
import com.viaversion.aas.protocol.id47toid5.storage.EntityTracker
import com.viaversion.aas.protocol.id47toid5.type.CustomIntType
import com.viaversion.aas.protocol.xyzToPosition
import com.viaversion.viaversion.api.minecraft.entities.Entity1_10Types
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.remapper.PacketRemapper
import com.viaversion.viaversion.api.protocol.remapper.TypeRemapper
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.types.version.Types1_8
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Types1_7_6_10
import kotlin.experimental.and

fun Protocol1_8To1_7_6.registerEntityPackets() {
    //Entity Equipment
    this.registerClientbound(State.PLAY, 0x04, 0x04, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Entity Id
            map(Type.SHORT) //Slot
            map(Types1_7_6_10.COMPRESSED_NBT_ITEM, Type.ITEM) //Item
        }
    })

    //Animation
    this.registerClientbound(State.PLAY, 0x0B, 0x0B, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val entityId = packetWrapper.read(Type.VAR_INT) //Entity Id
                val animation = packetWrapper.read(Type.UNSIGNED_BYTE) //Animation
                packetWrapper.clearInputBuffer()
                if (animation.toInt() == 104 || animation.toInt() == 105) {
                    packetWrapper.id = 0x1C //Entity Metadata
                    packetWrapper.write(Type.VAR_INT, entityId) //Entity Id
                    packetWrapper.write(Type.UNSIGNED_BYTE, 0.toShort()) //Index
                    packetWrapper.write(Type.UNSIGNED_BYTE, 0.toShort()) //Type
                    packetWrapper.write(
                        Type.BYTE,
                        (if (animation.toInt() == 104) 0x02 else 0x00).toByte()
                    ) //Value (sneaking/not sneaking)
                    packetWrapper.write(Type.UNSIGNED_BYTE, 255.toShort()) //end
                } else {
                    packetWrapper.write(Type.VAR_INT, entityId) //Entity Id
                    packetWrapper.write(Type.UNSIGNED_BYTE, animation) //Animation
                }
            }
        }
    })

    //Collect Item
    this.registerClientbound(State.PLAY, 0x0D, 0x0D, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Collected Entity ID
            map(Type.INT, Type.VAR_INT) //Collector Entity ID
        }
    })


    //Spawn Object
    this.registerClientbound(State.PLAY, 0x0E, 0x0E, object : PacketRemapper() {
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
                val type = packetWrapper.get(Type.BYTE, 0)
                var x = packetWrapper.get(Type.INT, 0)
                var y = packetWrapper.get(Type.INT, 1)
                var z = packetWrapper.get(Type.INT, 2)
                var yaw = packetWrapper.get(Type.BYTE, 2)
                var data = packetWrapper.get(Type.INT, 3)
                if (type.toInt() == 71) {
                    when (data) {
                        0 -> {
                            z += 32
                            yaw = 0
                        }
                        1 -> {
                            x -= 32
                            yaw = 64.toByte()
                        }
                        2 -> {
                            z -= 32
                            yaw = 128.toByte()
                        }
                        3 -> {
                            x += 32
                            yaw = 192.toByte()
                        }
                    }
                }
                if (type.toInt() == 70) {
                    val id = data
                    val metadata = data shr 16
                    data = id.or(metadata shl 12)
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
    this.registerClientbound(State.PLAY, 0x0F, 0x0F, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x10, 0x10, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x11, 0x11, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x12, 0x12, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Entity Id
            map(Type.SHORT) //velX
            map(Type.SHORT) //velY
            map(Type.SHORT) //velZ
        }
    })

    //Destroy Entities
    this.registerClientbound(State.PLAY, 0x13, 0x13, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x14, 0x14, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Entity Id
        }
    })

    //Entity Relative Move
    this.registerClientbound(State.PLAY, 0x15, 0x15, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x16, 0x16, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x17, 0x17, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x18, 0x18, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x19, 0x19, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Entity Id
            map(Type.BYTE) //Head yaw
        }
    })

    //Entity MetadataType
    this.registerClientbound(State.PLAY, 0x1C, 0x1C, object : PacketRemapper() {
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
    this.registerClientbound(State.PLAY, 0x1D, 0x1D, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Entity Id
            map(Type.BYTE) //Effect Id
            map(Type.BYTE) //Amplifier
            map(Type.SHORT, Type.VAR_INT) //Duration
            create { packetWrapper -> packetWrapper.write(Type.BOOLEAN, false) } //Hide Particles
        }
    })

    //Remove Entity Effect
    this.registerClientbound(State.PLAY, 0x1E, 0x1E, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Entity Id
            map(Type.BYTE) //Effect Id
        }
    })

    //Entity Properties
    this.registerClientbound(State.PLAY, 0x20, 0x20, object : PacketRemapper() {
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


    //Spawn Global Entity
    this.registerClientbound(State.PLAY, 0x2C, 0x2C, object : PacketRemapper() {
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

    //Use Entity
    this.registerServerbound(State.PLAY, 0x02, 0x02, object : PacketRemapper() {
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

    //Entity Action
    this.registerServerbound(State.PLAY, 0x0B, 0x0B, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.VAR_INT, Type.INT) //Entity Id
            handler { packetWrapper ->
                packetWrapper.write(
                    Type.BYTE,
                    (packetWrapper.read(Type.VAR_INT) + 1).toByte()
                )
            } //Action Id
            map(Type.VAR_INT, Type.INT) //Action Paramter
        }
    })

    //Steer Vehicle
    this.registerServerbound(State.PLAY, 0x0C, 0x0C, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.FLOAT) //Sideways
            map(Type.FLOAT) //Forwards
            handler { packetWrapper ->
                val flags = packetWrapper.read(Type.UNSIGNED_BYTE)
                packetWrapper.write(Type.BOOLEAN, flags and 1 == 1.toShort()) //Jump
                packetWrapper.write(Type.BOOLEAN, flags and 2 == 2.toShort()) //Unmount
            }
        }
    })
}