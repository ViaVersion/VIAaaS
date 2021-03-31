package com.github.creeper123123321.viaaas.protocol.id47toid5.chunks

import us.myles.ViaVersion.api.minecraft.chunks.NibbleArray

class ExtendedBlockStorage(val yLocation: Int, paramBoolean: Boolean) {
    var blockLSBArray: ByteArray = ByteArray(4096)
    var blockMSBArray: NibbleArray? = null
    var metadataArray: NibbleArray
        private set
    var blocklightArray: NibbleArray
    var skylightArray: NibbleArray? = null
    fun getExtBlockMetadata(paramInt1: Int, paramInt2: Int, paramInt3: Int): Int {
        return metadataArray[paramInt1, paramInt2, paramInt3].toInt()
    }

    fun setExtBlockMetadata(paramInt1: Int, paramInt2: Int, paramInt3: Int, paramInt4: Int) {
        metadataArray[paramInt1, paramInt2, paramInt3] = paramInt4
    }

    fun setExtSkylightValue(paramInt1: Int, paramInt2: Int, paramInt3: Int, paramInt4: Int) {
        skylightArray!![paramInt1, paramInt2, paramInt3] = paramInt4
    }

    fun getExtSkylightValue(paramInt1: Int, paramInt2: Int, paramInt3: Int): Int {
        return skylightArray!![paramInt1, paramInt2, paramInt3].toInt()
    }

    fun setExtBlocklightValue(paramInt1: Int, paramInt2: Int, paramInt3: Int, paramInt4: Int) {
        blocklightArray[paramInt1, paramInt2, paramInt3] = paramInt4
    }

    fun getExtBlocklightValue(paramInt1: Int, paramInt2: Int, paramInt3: Int): Int {
        return blocklightArray[paramInt1, paramInt2, paramInt3].toInt()
    }

    val isEmpty: Boolean
        get() = blockMSBArray == null

    fun clearMSBArray() {
        blockMSBArray = null
    }

    fun setBlockMetadataArray(paramNibbleArray: NibbleArray) {
        metadataArray = paramNibbleArray
    }

    fun createBlockMSBArray(): NibbleArray? {
        blockMSBArray = NibbleArray(blockLSBArray.size)
        return blockMSBArray
    }

    init {
        metadataArray = NibbleArray(blockLSBArray.size)
        blocklightArray = NibbleArray(blockLSBArray.size)
        if (paramBoolean) {
            skylightArray = NibbleArray(blockLSBArray.size)
        }
    }
}