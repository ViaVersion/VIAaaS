package com.github.creeper123123321.viaaas.protocol.id47toid5.storage

import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection

class Windows(user: UserConnection?) : StoredObject(user) {
    var types = mutableMapOf<Short, Short>()
    operator fun get(windowId: Short): Short = types.getOrDefault(windowId, (-1).toShort())
}