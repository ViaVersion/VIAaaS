package com.viaversion.aas.protocol.id47toid5.storage

import com.viaversion.viaversion.api.connection.StoredObject
import com.viaversion.viaversion.api.connection.UserConnection
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
        var mapIcons = emptyArray<MapIcon>()
    }
    class MapIcon(var direction: Byte, var type: Byte, var x: Byte, var z: Byte)
}