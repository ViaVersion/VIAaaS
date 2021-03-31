package com.github.creeper123123321.viaaas.protocol.id47toid5.storage

import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection
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