package com.github.creeper123123321.viaaas.protocol.id47toid5.metadata

import de.gerrygames.viarewind.protocol.protocol1_8to1_7_6_10.metadata.MetaIndex1_8to1_7_6_10
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.entities.Entity1_10Types
import us.myles.ViaVersion.api.minecraft.metadata.Metadata
import us.myles.ViaVersion.api.minecraft.metadata.types.MetaType1_8
import java.util.*

object MetadataRewriter {
    fun transform(type: Entity1_10Types.EntityType?, list: MutableList<Metadata>) {
        for (entry in ArrayList(list)) {
            val metaIndex = MetaIndex1_8to1_7_6_10.searchIndex(type, entry.id)
            try {
                if (metaIndex == null) throw Exception("Could not find valid metadata")
                if (metaIndex.newType == MetaType1_8.NonExistent) {
                    list.remove(entry)
                    return
                }
                val value = entry.value
                if (!value.javaClass.isAssignableFrom(metaIndex.oldType.type.outputClass)) {
                    list.remove(entry)
                    return
                }
                entry.metaType = metaIndex.newType
                entry.id = metaIndex.newIndex
                when (metaIndex.newType) {
                    MetaType1_8.Int -> entry.value = (value as Number).toInt()
                    MetaType1_8.Byte -> {
                        var byteValue = (value as Number).toByte()
                        entry.value = byteValue
                        if (metaIndex == MetaIndex1_8to1_7_6_10.HUMAN_SKIN_FLAGS) {
                            val cape = byteValue.toInt() == 2
                            byteValue = (if (cape) 127 else 125).toByte()
                            entry.value = byteValue
                        }
                    }
                    MetaType1_8.Short -> entry.value = (value as Number).toShort()
                    MetaType1_8.String -> entry.value = value.toString()
                    MetaType1_8.Float -> entry.value = (value as Number).toFloat()
                    MetaType1_8.Slot, MetaType1_8.Position, MetaType1_8.Rotation -> entry.value = value
                    else -> {
                        Via.getPlatform().logger.warning("[Out] Unhandled MetaDataType: " + metaIndex.newType)
                        list.remove(entry)
                    }
                }
            } catch (e: Exception) {
                list.remove(entry)
            }
        }
    }
}