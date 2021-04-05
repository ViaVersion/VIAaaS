package com.viaversion.aas.protocol.id47toid5

import com.viaversion.aas.protocol.id47toid5.packets.*
import com.viaversion.aas.protocol.id47toid5.storage.*
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.SimpleProtocol
import java.util.*

// Based on https://github.com/Gerrygames/ClientViaVersion
object Protocol1_8To1_7_6 : SimpleProtocol() {
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
        this.registerPlayerPackets()
        this.registerEntityPackets()
        this.registerWorldPackets()
        this.registerLoginPackets()
        this.registerInventoryPackets()
    }

    override fun init(userConnection: UserConnection) {
        userConnection.put(Tablist(userConnection))
        userConnection.put(Windows(userConnection))
        userConnection.put(Scoreboard(userConnection))
        userConnection.put(EntityTracker(userConnection))
        userConnection.put(MapStorage(userConnection))
    }

    fun isPlayerInsideBlock(x: Long, y: Long, z: Long, direction: Short): Boolean {
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

    fun isPlaceable(id: Int): Boolean {
        return placeable.contains(id)
    }

    fun getInventoryString(b: Int): String {
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