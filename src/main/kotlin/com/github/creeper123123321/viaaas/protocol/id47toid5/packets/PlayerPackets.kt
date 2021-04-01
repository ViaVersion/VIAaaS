package com.github.creeper123123321.viaaas.protocol.id47toid5.packets

import com.github.creeper123123321.viaaas.protocol.id47toid5.Protocol1_8To1_7_6
import com.github.creeper123123321.viaaas.protocol.id47toid5.metadata.MetadataRewriter
import com.github.creeper123123321.viaaas.protocol.id47toid5.storage.EntityTracker
import com.github.creeper123123321.viaaas.protocol.id47toid5.storage.Scoreboard
import com.github.creeper123123321.viaaas.protocol.id47toid5.storage.Tablist
import com.github.creeper123123321.viaaas.protocol.xyzToPosition
import com.github.creeper123123321.viaaas.protocol.xyzUBytePos
import com.google.common.base.Charsets
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.CustomStringType
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Types1_7_6_10
import de.gerrygames.viarewind.utils.ChatUtil
import io.netty.buffer.Unpooled
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.entities.Entity1_10Types
import us.myles.ViaVersion.api.minecraft.item.Item
import us.myles.ViaVersion.api.minecraft.metadata.Metadata
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.remapper.TypeRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.api.type.types.CustomByteType
import us.myles.ViaVersion.api.type.types.version.Types1_8
import us.myles.ViaVersion.packets.State
import us.myles.ViaVersion.util.ChatColorUtil
import us.myles.viaversion.libs.opennbt.tag.builtin.CompoundTag
import us.myles.viaversion.libs.opennbt.tag.builtin.ListTag
import us.myles.viaversion.libs.opennbt.tag.builtin.StringTag
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.and

fun Protocol1_8To1_7_6.registerPlayerPackets() {
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

    //Set Experience
    this.registerOutgoing(State.PLAY, 0x1F, 0x1F, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.FLOAT) //Experience bar
            map(Type.SHORT, Type.VAR_INT) //Level
            map(Type.SHORT, Type.VAR_INT) //Total Experience
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
                                var value: String? = page.value
                                value = ChatUtil.jsonToLegacy(value)
                                page.value = value
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

    //Animation
    this.registerIncoming(State.PLAY, 0x0A, 0x0A, object : PacketRemapper() {
        override fun registerMap() {
            create { packetWrapper ->
                packetWrapper.write(Type.INT, 0) //Entity Id, hopefully 0 is ok
                packetWrapper.write(Type.BYTE, 1.toByte()) //Animation
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
}
