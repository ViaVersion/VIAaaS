package com.viaversion.aas.protocol.id47toid5.metadata

import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.minecraft.entities.Entity1_10Types
import com.viaversion.viaversion.api.minecraft.metadata.Metadata
import com.viaversion.viaversion.api.minecraft.metadata.types.MetaType1_8
import de.gerrygames.viarewind.protocol.protocol1_8to1_7_6_10.metadata.MetaIndex1_8to1_7_6_10

object MetadataRewriter {
    fun transform(type: Entity1_10Types.EntityType?, list: MutableList<Metadata>) {
        for (entry in ArrayList(list)) {
            try {
                val oldValue = entry.value
                val metaIndex = MetaIndex1_8to1_7_6_10.searchIndex(type, entry.id())

                if (metaIndex == null || metaIndex.newType == MetaType1_8.NonExistent) {
                    list.remove(entry)
                    continue
                }

                entry.setId(metaIndex.newIndex)
                entry.setMetaTypeUnsafe(metaIndex.newType)
                when (metaIndex.newType) {
                    MetaType1_8.Byte -> {
                        var byteValue = (oldValue as Number).toByte()

                        if (metaIndex == MetaIndex1_8to1_7_6_10.HUMAN_SKIN_FLAGS) {
                            val cape = byteValue.toInt() == 2
                            byteValue = (if (cape) 127 else 125).toByte()
                        }
                        entry.value = byteValue
                    }
                    MetaType1_8.Int -> entry.value = (oldValue as Number).toInt()
                    MetaType1_8.Short -> entry.value = (oldValue as Number).toShort()
                    MetaType1_8.Float -> entry.value = (oldValue as Number).toFloat()
                    MetaType1_8.String -> entry.value = oldValue.toString()
                    MetaType1_8.Slot, MetaType1_8.Position, MetaType1_8.Rotation -> entry.value = oldValue
                    else -> throw Exception("unknown metatype ${metaIndex.newType}")
                }
            } catch (e: Exception) {
                if (!Via.getPlatform().conf.isSuppressMetadataErrors) {
                    Via.getPlatform().logger.warning("Metadata Exception: $e $list")
                }
                list.remove(entry)
            }
        }
    }
}