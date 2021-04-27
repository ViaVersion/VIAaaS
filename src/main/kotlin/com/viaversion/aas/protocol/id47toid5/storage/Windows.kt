package com.viaversion.aas.protocol.id47toid5.storage

import com.viaversion.viaversion.api.connection.StoredObject
import com.viaversion.viaversion.api.connection.UserConnection

class Windows(user: UserConnection?) : StoredObject(user) {
    var types = mutableMapOf<Short, Short>()
    operator fun get(windowId: Short): Short = types.getOrDefault(windowId, (-1).toShort())
}