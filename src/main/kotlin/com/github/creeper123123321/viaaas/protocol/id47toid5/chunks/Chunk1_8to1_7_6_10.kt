package com.github.creeper123123321.viaaas.protocol.id47toid5.chunks

class Chunk1_8to1_7_6_10(data: ByteArray?, private val primaryBitMask: Int, addBitMask: Int, private val skyLight: Boolean, private val groundUp: Boolean) {
    var storageArrays = arrayOfNulls<ExtendedBlockStorage>(16)
    var blockBiomeArray = ByteArray(256)
    fun get1_8Data(): ByteArray {
        var finalSize = 0
        val columns = Integer.bitCount(primaryBitMask)
        val buffer = ByteArray(columns * 10240 + (if (skyLight) columns * 2048 else 0) + 256)
        for (i in storageArrays.indices) {
            if (storageArrays[i] != null && primaryBitMask and 1 shl i != 0 && (!groundUp || storageArrays[i]!!.isEmpty)) {
                val blockIds = storageArrays[i]!!.blockLSBArray
                val nibblearray = storageArrays[i]!!.metadataArray
                for (ind in blockIds.indices) {
                    val id = blockIds[ind].toInt() and 255
                    val px = ind and 15
                    val py = ind shr 8 and 15
                    val pz = ind shr 4 and 15
                    val data = nibblearray[px, py, pz].toInt()

                    //data = SpigotDebreakifier.getCorrectedData(id, data);
                    val `val` = (id shl 4 or data).toChar()
                    buffer[finalSize++] = (`val`.toInt() and 255).toByte()
                    buffer[finalSize++] = (`val`.toInt() shr 8 and 255).toByte()
                }
            }
        }
        for (i in storageArrays.indices) {
            if (storageArrays[i] != null && primaryBitMask and 1 shl i != 0 && (!groundUp || storageArrays[i]!!.isEmpty)) {
                val nibblearray = storageArrays[i]!!.blocklightArray
                System.arraycopy(nibblearray.handle, 0, buffer, finalSize, nibblearray.handle.size)
                finalSize += nibblearray.handle.size
            }
        }
        if (skyLight) {
            for (i in storageArrays.indices) {
                if (storageArrays[i] != null && primaryBitMask and 1 shl i != 0 && (!groundUp || storageArrays[i]!!.isEmpty)) {
                    val nibblearray = storageArrays[i]!!.skylightArray
                    System.arraycopy(nibblearray!!.handle, 0, buffer, finalSize, nibblearray!!.handle.size)
                    finalSize += nibblearray!!.handle.size
                }
            }
        }
        if (groundUp) {
            System.arraycopy(blockBiomeArray, 0, buffer, finalSize, blockBiomeArray.size)
            finalSize += blockBiomeArray.size
        }
        val finaldata = ByteArray(finalSize)
        System.arraycopy(buffer, 0, finaldata, 0, finalSize)
        return finaldata
    }

    init {
        var dataSize = 0
        for (i in storageArrays.indices) {
            if (primaryBitMask and 1 shl i != 0) {
                if (storageArrays[i] == null) storageArrays[i] = ExtendedBlockStorage(i shl 4, skyLight)
                val blockIds = storageArrays[i]!!.blockLSBArray
                System.arraycopy(data, dataSize, blockIds, 0, blockIds!!.size)
                dataSize += blockIds!!.size
            } else if (storageArrays[i] != null && groundUp) {
                storageArrays[i] = null
            }
        }
        for (i in storageArrays.indices) {
            if (primaryBitMask and 1 shl i != 0 && storageArrays[i] != null) {
                val nibblearray = storageArrays[i]!!.metadataArray
                System.arraycopy(data, dataSize, nibblearray!!.handle, 0, nibblearray!!.handle.size)
                dataSize += nibblearray!!.handle.size
            }
        }
        for (i in storageArrays.indices) {
            if (primaryBitMask and 1 shl i != 0 && storageArrays[i] != null) {
                val nibblearray = storageArrays[i]!!.blocklightArray
                System.arraycopy(data, dataSize, nibblearray!!.handle, 0, nibblearray!!.handle.size)
                dataSize += nibblearray!!.handle.size
            }
        }
        if (skyLight) {
            for (i in storageArrays.indices) {
                if (primaryBitMask and 1 shl i != 0 && storageArrays[i] != null) {
                    val nibblearray = storageArrays[i]!!.skylightArray
                    System.arraycopy(data, dataSize, nibblearray!!.handle, 0, nibblearray!!.handle.size)
                    dataSize += nibblearray!!.handle.size
                }
            }
        }
        for (i in storageArrays.indices) {
            if (addBitMask and 1 shl i != 0) {
                if (storageArrays[i] == null) {
                    dataSize += 2048
                } else {
                    var nibblearray = storageArrays[i]!!.blockMSBArray
                    if (nibblearray == null) {
                        nibblearray = storageArrays[i]!!.createBlockMSBArray()
                    }
                    System.arraycopy(data, dataSize, nibblearray!!.handle, 0, nibblearray!!.handle.size)
                    dataSize += nibblearray!!.handle.size
                }
            } else if (groundUp && storageArrays[i] != null && storageArrays[i]!!.blockMSBArray != null) {
                storageArrays[i]!!.clearMSBArray()
            }
        }
        if (groundUp) {
            System.arraycopy(data, dataSize, blockBiomeArray, 0, blockBiomeArray.size)
        }
    }
}