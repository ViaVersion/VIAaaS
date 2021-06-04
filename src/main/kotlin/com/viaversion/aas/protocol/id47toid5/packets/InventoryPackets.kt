package com.viaversion.aas.protocol.id47toid5.packets

import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.aas.protocol.id47toid5.storage.Windows
import com.viaversion.viaversion.api.minecraft.item.DataItem
import com.viaversion.viaversion.api.minecraft.item.Item
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.remapper.PacketRemapper
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.libs.kyori.adventure.text.Component
import com.viaversion.viaversion.libs.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import com.viaversion.viaversion.libs.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.types.Types1_7_6_10

fun Protocol1_8To1_7_6.registerInventoryPackets() {

    //Open Window
    this.registerClientbound(State.PLAY, 0x2D, 0x2D, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.UNSIGNED_BYTE)
            handler { packetWrapper ->
                val windowId = packetWrapper.get(Type.UNSIGNED_BYTE, 0)
                val windowType = packetWrapper.read(Type.UNSIGNED_BYTE)
                packetWrapper.user().get(Windows::class.java)!!.types[windowId] = windowType
                packetWrapper.write(Type.STRING, getInventoryString(windowType.toInt())) //Inventory Type
                val title = packetWrapper.read(Type.STRING) //Title
                val slots = packetWrapper.read(Type.UNSIGNED_BYTE)
                val useProvidedWindowTitle = packetWrapper.read(Type.BOOLEAN) //Use provided window title
                val convertedTitle = GsonComponentSerializer.gson().serialize(
                    if (useProvidedWindowTitle) {
                        LegacyComponentSerializer.legacySection().deserialize(title)
                    } else {
                        Component.translatable(title) // todo
                    }
                )
                packetWrapper.write(Type.STRING, convertedTitle) //Window title
                packetWrapper.write(Type.UNSIGNED_BYTE, slots)
                if (windowType == 11.toShort()) packetWrapper.passthrough(Type.INT) //Entity Id
            }
        }
    })

    //Set Slot
    this.registerClientbound(State.PLAY, 0x2F, 0x2F, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val windowId = packetWrapper.read(Type.BYTE).toShort() //Window Id
                val windowType = packetWrapper.user().get(Windows::class.java)!!.get(windowId).toShort()
                packetWrapper.write(Type.BYTE, windowId.toByte())
                var slot = packetWrapper.read(Type.SHORT).toInt()
                if (windowType.toInt() == 4 && slot >= 1) slot += 1
                packetWrapper.write(Type.SHORT, slot.toShort()) //Slot
            }
            map(Types1_7_6_10.COMPRESSED_NBT_ITEM, Type.ITEM) //Item
        }
    })

    //Window Items
    this.registerClientbound(State.PLAY, 0x30, 0x30, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.UNSIGNED_BYTE) //Window Id
            handler { packetWrapper ->
                val windowId = packetWrapper.get(Type.UNSIGNED_BYTE, 0)
                val windowType = packetWrapper.user().get(Windows::class.java)!![windowId]
                var items = packetWrapper.read(Types1_7_6_10.COMPRESSED_NBT_ITEM_ARRAY)
                if (windowType.toInt() == 4) {
                    val old = items
                    items = arrayOfNulls(old.size + 1)
                    items[0] = old[0]
                    System.arraycopy(old, 1, items, 2, old.size - 1)
                    items[1] = DataItem(351, 3.toByte(), 4.toShort(), null)
                }
                packetWrapper.write(Type.ITEM_ARRAY, items) //Items
            }
        }
    })


    //Click Window
    this.registerServerbound(State.PLAY, 0x0E, 0x0E, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val windowId = packetWrapper.read(Type.UNSIGNED_BYTE) //Window Id
                packetWrapper.write(Type.BYTE, windowId.toByte())
                val windowType = packetWrapper.user().get(Windows::class.java)!![windowId]
                var slot = packetWrapper.read(Type.SHORT).toInt()
                if (windowType.toInt() == 4) {
                    if (slot == 1) {
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
    this.registerServerbound(State.PLAY, 0x10, 0x10, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.SHORT) //Slot
            map(Type.ITEM, Types1_7_6_10.COMPRESSED_NBT_ITEM) //Item
        }
    })
}
