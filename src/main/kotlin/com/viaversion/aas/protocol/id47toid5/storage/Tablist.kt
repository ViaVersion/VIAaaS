package com.viaversion.aas.protocol.id47toid5.storage

import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection
import java.util.*

class Tablist(user: UserConnection?) : StoredObject(user) {
    private val tablist = ArrayList<TabListEntry>()
    fun getTabListEntry(name: String): TabListEntry? {
        for (entry in tablist) if (name == entry.name) return entry
        return null
    }

    fun getTabListEntry(uuid: UUID): TabListEntry? {
        for (entry in tablist) if (uuid == entry.uuid) return entry
        return null
    }

    fun remove(entry: TabListEntry) {
        tablist.remove(entry)
    }

    fun add(entry: TabListEntry) {
        tablist.add(entry)
    }

    class TabListEntry(var name: String, var uuid: UUID) {
        var displayName: String? = null
        var ping = 0
        var properties = mutableListOf<Property>()
    }

    class Property(var name: String?, var value: String?, var signature: String?)
    companion object {
        fun shouldUpdateDisplayName(oldName: String?, newName: String?): Boolean {
            return oldName == null && newName != null || oldName != null && newName == null || oldName != null && oldName != newName
        }
    }
}