package com.github.creeper123123321.viaaas.protocol.id47toid5.storage

import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection
import java.util.*

class MapStorage(user: UserConnection) : StoredObject(user) {
    private val maps: MutableMap<Int, MapData> = HashMap()
    fun getMapData(id: Int): MapData? {
        return maps[id]
    }

    fun putMapData(id: Int, mapData: MapData) {
        maps[id] = mapData
    }

    class MapData {
        var scale: Byte = 0
        var mapIcons = arrayOf<MapIcon>()
    }
    class MapIcon(var direction: Byte, var type: Byte, var x: Byte, var z: Byte)
}