package com.viaversion.aas.protocol.id47toid5.packets

import com.google.common.base.Charsets
import com.viaversion.aas.generateOfflinePlayerUuid
import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.aas.protocol.id47toid5.metadata.MetadataRewriter
import com.viaversion.aas.protocol.id47toid5.storage.EntityTracker
import com.viaversion.aas.protocol.id47toid5.storage.Scoreboard
import com.viaversion.aas.protocol.id47toid5.storage.Tablist
import com.viaversion.aas.protocol.xyzToPosition
import com.viaversion.aas.protocol.xyzUBytePos
import com.viaversion.viaversion.api.minecraft.entities.Entity1_10Types
import com.viaversion.viaversion.api.minecraft.item.Item
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.remapper.PacketRemapper
import com.viaversion.viaversion.api.protocol.remapper.TypeRemapper
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.types.CustomByteType
import com.viaversion.viaversion.api.type.types.version.Types1_8
import com.viaversion.viaversion.libs.opennbt.tag.builtin.ListTag
import com.viaversion.viaversion.libs.opennbt.tag.builtin.StringTag
import com.viaversion.viaversion.protocols.protocol1_8.ClientboundPackets1_8
import com.viaversion.viaversion.protocols.protocol1_8.ServerboundPackets1_8
import com.viaversion.viaversion.util.ChatColorUtil
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.ClientboundPackets1_7
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.CustomStringType
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Types1_7_6_10
import de.gerrygames.viarewind.utils.ChatUtil
import io.netty.buffer.Unpooled
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.experimental.and

fun Protocol1_8To1_7_6.registerPlayerPackets() {
    this.registerClientbound(ClientboundPackets1_7.KEEP_ALIVE, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT)
        }
    })

    this.registerClientbound(ClientboundPackets1_7.JOIN_GAME, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT) //Entiy Id
            map(Type.UNSIGNED_BYTE) //Gamemode
            map(Type.BYTE) //Dimension
            map(Type.UNSIGNED_BYTE) //Difficulty
            map(Type.UNSIGNED_BYTE) //Max players
            map(Type.STRING) //Level Type
            create(Type.BOOLEAN, false) //Reduced Debug Info
        }
    })

    this.registerClientbound(ClientboundPackets1_7.CHAT_MESSAGE, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.STRING) //Chat Message
            create(Type.BYTE, 0.toByte()) //Position (chat box)
        }
    })

    this.registerClientbound(ClientboundPackets1_7.SPAWN_POSITION, object : PacketRemapper() {
        override fun registerMap() {
            map(xyzToPosition, TypeRemapper(Type.POSITION)) //Position
        }
    })

    this.registerClientbound(ClientboundPackets1_7.UPDATE_HEALTH, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.FLOAT) //Health
            map(Type.SHORT, Type.VAR_INT) //Food
            map(Type.FLOAT) //Food Saturation
        }
    })

    this.registerClientbound(ClientboundPackets1_7.PLAYER_POSITION, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.DOUBLE) //x
            handler { packetWrapper ->
                val y = packetWrapper.read(Type.DOUBLE)
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

    this.registerClientbound(ClientboundPackets1_7.USE_BED, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.INT, Type.VAR_INT) //Entity Id
            map(xyzUBytePos, TypeRemapper(Type.POSITION))
        }
    })

    this.registerClientbound(ClientboundPackets1_7.SPAWN_PLAYER, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val entityId = packetWrapper.passthrough(Type.VAR_INT) //Entity Id
                val uuid = UUID.fromString(packetWrapper.read(Type.STRING)) //UUID
                packetWrapper.write(Type.UUID, uuid)
                val name = ChatColorUtil.stripColor(packetWrapper.read(Type.STRING)) //Name
                val dataCount = packetWrapper.read(Type.VAR_INT) //DataCunt
                val properties = ArrayList<Tablist.Property>()
                for (i in 0 until dataCount) {
                    val key: String = packetWrapper.read(Type.STRING) //Name
                    val value: String = packetWrapper.read(Type.STRING) //Value
                    val signature: String = packetWrapper.read(Type.STRING) //Signature
                    properties.add(Tablist.Property(key, value, signature))
                }
                val x = packetWrapper.passthrough(Type.INT) //x
                val y = packetWrapper.passthrough(Type.INT) //y
                val z = packetWrapper.passthrough(Type.INT) //z
                val yaw = packetWrapper.passthrough(Type.BYTE) //yaw
                val pitch = packetWrapper.passthrough(Type.BYTE) //pitch
                val item = packetWrapper.passthrough(Type.SHORT) //Item in hand
                val metadata = packetWrapper.read(Types1_7_6_10.METADATA_LIST) //Metadata
                MetadataRewriter.transform(Entity1_10Types.EntityType.PLAYER, metadata)
                packetWrapper.write(Types1_8.METADATA_LIST, metadata)
                val tablist = packetWrapper.user().get(Tablist::class.java)!!
                var entryByName = tablist.getTabListEntry(name)
                if (entryByName == null && name.length > 14) entryByName =
                    tablist.getTabListEntry(name.substring(0, 14))
                val entryByUUID = tablist.getTabListEntry(uuid)
                if (entryByName == null || entryByUUID == null) {
                    if (entryByName != null || entryByUUID != null) {
                        val remove = PacketWrapper.create(ClientboundPackets1_8.PLAYER_INFO, null, packetWrapper.user())
                        remove.write(Type.VAR_INT, 4)
                        remove.write(Type.VAR_INT, 1)
                        remove.write(Type.UUID, entryByName?.uuid ?: entryByUUID!!.uuid)
                        tablist.remove(entryByName ?: entryByUUID!!)
                        remove.send(Protocol1_8To1_7_6::class.java)
                    }
                    val packetPlayerListItem =
                        PacketWrapper.create(ClientboundPackets1_8.PLAYER_INFO, null, packetWrapper.user())
                    val newentry = Tablist.TabListEntry(name, uuid)
                    if (entryByName != null || entryByUUID != null) {
                        newentry.displayName =
                            if (entryByUUID != null) entryByUUID.displayName else entryByName!!.displayName
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
                    val delayedPacket =
                        PacketWrapper.create(ClientboundPackets1_8.SPAWN_PLAYER, null, packetWrapper.user())
                    delayedPacket.write(Type.VAR_INT, entityId)
                    delayedPacket.write(Type.UUID, uuid)
                    delayedPacket.write(Type.INT, x)
                    delayedPacket.write(Type.INT, y)
                    delayedPacket.write(Type.INT, z)
                    delayedPacket.write(Type.BYTE, yaw)
                    delayedPacket.write(Type.BYTE, pitch)
                    delayedPacket.write(Type.SHORT, item)
                    delayedPacket.write(Types1_8.METADATA_LIST, metadata)

                    delayedPacket.send(Protocol1_8To1_7_6::class.java)
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

    this.registerClientbound(ClientboundPackets1_7.SET_EXPERIENCE, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.FLOAT) //Experience bar
            map(Type.SHORT, Type.VAR_INT) //Level
            map(Type.SHORT, Type.VAR_INT) //Total Experience
        }
    })


    this.registerClientbound(ClientboundPackets1_7.PLAYER_INFO, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val name = packetWrapper.read(Type.STRING)
                val displayName: String? = null
                val online = packetWrapper.read(Type.BOOLEAN)
                val ping = packetWrapper.read(Type.SHORT)
                val tablist = packetWrapper.user().get(Tablist::class.java)!!
                var entry = tablist.getTabListEntry(name)
                if (!online && entry != null) {
                    packetWrapper.write(Type.VAR_INT, 4)
                    packetWrapper.write(Type.VAR_INT, 1)
                    packetWrapper.write(Type.UUID, entry.uuid)
                    tablist.remove(entry)
                } else if (online && entry == null) {
                    val uuid = if (name == packetWrapper.user().protocolInfo?.username) {
                        packetWrapper.user().protocolInfo!!.uuid!!
                    } else {
                        generateOfflinePlayerUuid(name)

                    }
                    entry = Tablist.TabListEntry(name, uuid)
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

    this.registerClientbound(ClientboundPackets1_7.SCOREBOARD_OBJECTIVE, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.STRING) // name
            handler { packetWrapper ->
                val value = packetWrapper.read(Type.STRING)
                val mode = packetWrapper.read(Type.BYTE)
                packetWrapper.write(Type.BYTE, mode)
                if (mode.toInt() == 0 || mode.toInt() == 2) {
                    packetWrapper.write(Type.STRING, value)
                    packetWrapper.write(Type.STRING, "integer")
                }
            }
        }
    })

    this.registerClientbound(ClientboundPackets1_7.UPDATE_SCORE, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val name = packetWrapper.passthrough(Type.STRING)
                val mode = packetWrapper.passthrough(Type.BYTE)
                if (mode.toInt() != 1) {
                    val objective = packetWrapper.passthrough(Type.STRING)
                    packetWrapper.user().get(Scoreboard::class.java)!!.put(name, objective)
                    packetWrapper.write(Type.VAR_INT, packetWrapper.read(Type.INT))
                } else {
                    val objective = packetWrapper.user().get(Scoreboard::class.java)!!.get(name)
                    packetWrapper.write(Type.STRING, objective)
                }
            }
        }
    })

    this.registerClientbound(ClientboundPackets1_7.TEAMS, object : PacketRemapper() {
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
                    val entries = packetWrapper.read(type)
                    packetWrapper.write(Type.STRING_ARRAY, entries)
                }
            }
        }
    })

    this.registerClientbound(ClientboundPackets1_7.PLUGIN_MESSAGE, object : PacketRemapper() {
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

    this.registerServerbound(ServerboundPackets1_8.KEEP_ALIVE, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.VAR_INT, Type.INT)
        }
    })

    this.registerServerbound(ServerboundPackets1_8.PLUGIN_MESSAGE, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.STRING)
            handler { packetWrapper ->
                val channel = packetWrapper.get(Type.STRING, 0)
                if (channel.equals("MC|ItemName", ignoreCase = true)) {
                    val name: ByteArray = packetWrapper.read(Type.STRING).toByteArray(Charsets.UTF_8)
                    packetWrapper.write(Type.REMAINING_BYTES, name)
                } else if (channel.equals("MC|BEdit", ignoreCase = true) || channel.equals(
                        "MC|BSign",
                        ignoreCase = true
                    )
                ) {
                    packetWrapper.read(Type.SHORT) //length
                    val book: Item = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM)
                    val tag = book.tag()
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
                packetWrapper.packetType = null
                val newPacketBuf = Unpooled.buffer()
                packetWrapper.writeToBuffer(newPacketBuf)
                val newWrapper =
                    PacketWrapper.create(ServerboundPackets1_8.PLUGIN_MESSAGE, newPacketBuf, packetWrapper.user())
                newWrapper.passthrough(Type.STRING)
                newWrapper.write(Type.SHORT, newPacketBuf.readableBytes().toShort())
                newWrapper.sendToServer(Protocol1_8To1_7_6::class.java)
            }
        }
    })

    this.registerServerbound(ServerboundPackets1_8.PLAYER_POSITION, object : PacketRemapper() {
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

    this.registerServerbound(ServerboundPackets1_8.PLAYER_POSITION_AND_ROTATION, object : PacketRemapper() {
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

    this.registerServerbound(ServerboundPackets1_8.ANIMATION, object : PacketRemapper() {
        override fun registerMap() {
            create(Type.INT, 0) //Entity Id, hopefully 0 is ok
            create(Type.BYTE, 1.toByte()) //Animation
        }
    })

    this.registerServerbound(ServerboundPackets1_8.CLIENT_SETTINGS, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.STRING)
            map(Type.BYTE)
            map(Type.BYTE)
            map(Type.BOOLEAN)
            create(Type.BYTE, 0.toByte())
            handler { packetWrapper ->
                val flags = packetWrapper.read(Type.UNSIGNED_BYTE)
                packetWrapper.write(Type.BOOLEAN, flags and 1 == 1.toShort())
            }
        }
    })

    this.registerServerbound(ServerboundPackets1_8.TAB_COMPLETE, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val text = packetWrapper.read(Type.STRING)
                packetWrapper.clearInputBuffer()
                packetWrapper.write(Type.STRING, text)
            }
        }
    })

    this.cancelServerbound(ServerboundPackets1_8.SPECTATE)
    this.cancelServerbound(ServerboundPackets1_8.RESOURCE_PACK_STATUS)
}
