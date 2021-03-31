package com.github.creeper123123321.viaaas.protocol.id47toid5.chunks

import com.github.creeper123123321.viaaas.readByteArray
import com.github.creeper123123321.viaaas.readRemainingBytes
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled

class Chunk1_8to1_7_6_10(
    data: ByteArray?,
    private val primaryBitMask: Int,
    addBitMask: Int,
    private val skyLight: Boolean,
    private val groundUp: Boolean
) {
    var storageSections = arrayOfNulls<ExtendedBlockStorage>(16)
    var blockBiomeArray = ByteArray(256)

    fun filterChunk(storageArray: ExtendedBlockStorage?, i: Int) =
        storageArray != null && primaryBitMask and 1 shl i != 0
                && (!groundUp || storageArray.isEmpty)

    fun get1_8Data(): ByteArray {
        val buf = ByteBufAllocator.DEFAULT.buffer()
        try {
            var finalSize = 0
            val filteredChunks = storageSections.filterIndexed { i, value -> filterChunk(value, i) }.filterNotNull()
            filteredChunks.forEach {
                val blockIds = it.blockLSBArray
                val nibblearray = it.metadataArray
                for (ind in blockIds.indices) {
                    val id = blockIds[ind].toInt() and 255
                    val px = ind and 15
                    val py = ind shr 8 and 15
                    val pz = ind shr 4 and 15
                    val data = nibblearray[px, py, pz].toInt()

                    //data = SpigotDebreakifier.getCorrectedData(id, data);
                    val `val` = (id shl 4 or data).toChar()
                    buf.writeByte(`val`.toInt() and 255)
                    buf.writeByte(`val`.toInt() shr 8 and 255)
                }
            }
            filteredChunks.forEach {
                buf.writeBytes(it.blocklightArray.handle)
            }
            if (skyLight) {
                filteredChunks.forEach {
                    buf.writeBytes(it.skylightArray!!.handle)
                }
            }
            if (groundUp) {
                buf.writeBytes(blockBiomeArray)
            }
            return readRemainingBytes(buf)
        } finally {
            buf.release()
        }
    }

    init {
        val input = Unpooled.wrappedBuffer(data)
        for (i in storageSections.indices) {
            if (primaryBitMask and 1 shl i != 0) {
                val storageSection = storageSections.getOrElse(i) {
                    ExtendedBlockStorage(i shl 4, skyLight).also { storageSections[i] = it }
                }!!
                storageSection.blockLSBArray = input.readByteArray(4096)
            } else if (storageSections[i] != null && groundUp) {
                storageSections[i] = null
            }
        }
        for (i in storageSections.indices) {
            if (primaryBitMask and 1 shl i != 0 && storageSections[i] != null) {
                storageSections[i]!!.metadataArray.handle = input.readByteArray(4096 / 2)
            }
        }
        for (i in storageSections.indices) {
            if (primaryBitMask and 1 shl i != 0 && storageSections[i] != null) {
                 storageSections[i]!!.blocklightArray.handle = input.readByteArray(4096 / 2)
            }
        }
        if (skyLight) {
            for (i in storageSections.indices) {
                if (primaryBitMask and 1 shl i != 0 && storageSections[i] != null) {
                    storageSections[i]!!.skylightArray!!.handle = input.readByteArray(4096 / 2)
                }
            }
        }
        for (i in storageSections.indices) {
            if (addBitMask and 1 shl i != 0) {
                if (storageSections[i] == null) {
                    input.skipBytes(2048)
                } else {
                    var msbArray = storageSections[i]!!.blockMSBArray ?: storageSections[i]!!.createBlockMSBArray()
                    msbArray!!.handle = input.readByteArray(4096 / 2)
                }
            } else if (groundUp && storageSections[i] != null && storageSections[i]!!.blockMSBArray != null) {
                storageSections[i]!!.clearMSBArray()
            }
        }
        if (groundUp) {
            blockBiomeArray = input.readByteArray(256)
        }
    }
}