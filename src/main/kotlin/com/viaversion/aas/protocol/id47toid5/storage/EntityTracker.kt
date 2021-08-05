package com.viaversion.aas.protocol.id47toid5.storage

import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.aas.protocol.id47toid5.metadata.MetadataRewriter
import com.viaversion.viaversion.api.connection.StoredObject
import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.api.minecraft.entities.Entity1_10Types
import com.viaversion.viaversion.api.minecraft.metadata.Metadata
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.types.version.Types1_8
import de.gerrygames.viarewind.protocol.protocol1_7_6_10to1_8.ClientboundPackets1_7
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
        val wrapper = PacketWrapper.create(ClientboundPackets1_7.ENTITY_METADATA, null, this.user)
        wrapper.write(Type.VAR_INT, entityId)
        wrapper.write(Types1_8.METADATA_LIST, metadataBuffer[entityId])
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