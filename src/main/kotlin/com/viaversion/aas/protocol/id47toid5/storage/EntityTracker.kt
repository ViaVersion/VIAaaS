package com.viaversion.aas.protocol.id47toid5.storage

import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.aas.protocol.id47toid5.metadata.MetadataRewriter
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.entities.Entity1_10Types
import us.myles.ViaVersion.api.minecraft.metadata.Metadata
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.api.type.types.version.Types1_8
import java.lang.Exception
import java.util.concurrent.ConcurrentHashMap

class EntityTracker(user: UserConnection) : StoredObject(user) {
    val clientEntityTypes = ConcurrentHashMap<Int, Entity1_10Types.EntityType>()
    private val metadataBuffer = ConcurrentHashMap<Int, MutableList<Metadata>>()
    fun removeEntity(entityId: Int) {
        clientEntityTypes.remove(entityId)
    }

    fun addMetadataToBuffer(entityID: Int, metadataList: MutableList<Metadata>) {
        metadataBuffer.computeIfAbsent(entityID) { mutableListOf() }.addAll(metadataList)
    }

    fun getBufferedMetadata(entityId: Int): List<Metadata> {
        return metadataBuffer[entityId]!!
    }

    fun sendMetadataBuffer(entityId: Int) {
        if (!metadataBuffer.containsKey(entityId)) return
        val wrapper = PacketWrapper(0x1C, null, this.user)
        wrapper.write(Type.VAR_INT, entityId)
        wrapper.write<List<Metadata>>(Types1_8.METADATA_LIST, metadataBuffer[entityId])
        MetadataRewriter.transform(clientEntityTypes[entityId], metadataBuffer[entityId]!!)
        if (metadataBuffer[entityId]!!.isNotEmpty()) {
            try {
                wrapper.send(Protocol1_8To1_7_6::class.java)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
        metadataBuffer.remove(entityId)
    }
}