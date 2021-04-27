package com.viaversion.aas.protocol.id47toid5.storage

import com.viaversion.viaversion.api.connection.StoredObject
import com.viaversion.viaversion.api.connection.UserConnection
import java.util.*

class Scoreboard(user: UserConnection) : StoredObject(user) {
    private val objectives = HashMap<String, String>()
    fun put(name: String, objective: String) {
        objectives[name] = objective
    }

    operator fun get(name: String): String {
        return objectives.getOrDefault(name, "null")
    }
}