package com.viaversion.aas.protocol.id47toid5.chunks

import com.viaversion.viaversion.api.minecraft.BlockChangeRecord1_8
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.type.types.CustomByteType
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import java.util.stream.IntStream
import java.util.zip.Inflater
import kotlin.streams.toList

object ChunkPacketTransformer {
    @Throws(Exception::class)
    fun transformChunk(packetWrapper: PacketWrapper) {
        val chunkX = packetWrapper.read(Type.INT)
        val chunkZ = packetWrapper.read(Type.INT)
        val groundUp = packetWrapper.read(Type.BOOLEAN)
        val primaryBitMask = packetWrapper.read(Type.SHORT).toInt()
        val addBitMask = packetWrapper.read(Type.SHORT).toInt()
        val compressedSize = packetWrapper.read(Type.INT)
        val customByteType = CustomByteType(compressedSize)
        val data = packetWrapper.read(customByteType)
        var countOfPrimary = 0
        var countOfAdditional = 0
        for (j in 0..15) {
            countOfPrimary += primaryBitMask.shr(j).and(1)
            countOfAdditional += addBitMask.shr(j).and(1)
        }
        var uncompressedSize = 12288 * countOfPrimary
        uncompressedSize += 2048 * countOfAdditional
        if (groundUp) {
            uncompressedSize += 256
        }
        val uncompressedData = ByteArray(uncompressedSize)
        val inflater = Inflater()
        inflater.setInput(data, 0, compressedSize)
        try {
            inflater.inflate(uncompressedData)
        } finally {
            inflater.end()
        }
        val chunk = Chunk1_8to1_7_6_10(uncompressedData, primaryBitMask, addBitMask, true, groundUp)

        packetWrapper.clearPacket()
        val buffer = (packetWrapper as PacketWrapperImpl).inputBuffer!!

        buffer.clear()
        buffer.writeInt(chunkX)
        buffer.writeInt(chunkZ)
        buffer.writeBoolean(groundUp)
        buffer.writeShort(primaryBitMask)
        val finalData = chunk.get1_8Data()
        Type.BYTE_ARRAY_PRIMITIVE.write(buffer, finalData)
    }

    @Throws(Exception::class)
    fun transformChunkBulk(packetWrapper: PacketWrapper) {
        val columnCount = packetWrapper.read(Type.SHORT).toInt()
        val compressedSize = packetWrapper.read(Type.INT)
        val skyLightSent = packetWrapper.read(Type.BOOLEAN)
        val chunkX = IntArray(columnCount)
        val chunkZ = IntArray(columnCount)
        val primaryBitMask = IntArray(columnCount)
        val compressedData = packetWrapper.read(CustomByteType(compressedSize))

        val decompressedData = ByteArray(196864 * columnCount)
        val inflater = Inflater()
        try {
            inflater.setInput(compressedData)
            inflater.inflate(decompressedData)
        } finally {
            inflater.end()
        }

        val decompressedBuf = Unpooled.wrappedBuffer(decompressedData)

        val chunks = arrayOfNulls<Chunk1_8to1_7_6_10>(columnCount)

        for (column in 0 until columnCount) {
            chunkX[column] = packetWrapper.read(Type.INT)
            chunkZ[column] = packetWrapper.read(Type.INT)
            primaryBitMask[column] = packetWrapper.read(Type.SHORT).toInt()
            val addBitMask = packetWrapper.read(Type.SHORT).toInt()

            var primaryCount = 0
            var secondaryCount = 0
            for (chunkY in 0 until 16) {
                primaryCount += primaryBitMask[column].shr(chunkY).and(1)
                secondaryCount += addBitMask.shr(chunkY).and(1)
            }

            var copySize = 8192 * primaryCount + 256
            copySize += 2048 * secondaryCount
            if (skyLightSent) {
                copySize += 2048 * primaryCount
            }

            val columnData = ByteArray(copySize)
            decompressedBuf.readBytes(columnData)

            chunks[column] = Chunk1_8to1_7_6_10(
                columnData, primaryBitMask[column],
                addBitMask, skyLightSent, true
            )
        }

        packetWrapper.write(Type.BOOLEAN, skyLightSent)
        packetWrapper.write(Type.VAR_INT, columnCount)

        for (column in 0 until columnCount) {
            packetWrapper.write(Type.INT, chunkX[column])
            packetWrapper.write(Type.INT, chunkZ[column])
            packetWrapper.write(Type.UNSIGNED_SHORT, primaryBitMask[column])
        }

        for (column in 0 until columnCount) {
            val columnData = chunks[column]!!.get1_8Data()
            packetWrapper.write(CustomByteType(columnData.size), columnData)
        }

        val buffer = ByteBufAllocator.DEFAULT.buffer()
        try {
            packetWrapper.writeToBuffer(buffer)
            Type.VAR_INT.readPrimitive(buffer) // packet id
            packetWrapper.clearPacket()

            (packetWrapper as PacketWrapperImpl).inputBuffer!!.writeBytes(buffer)
        } finally {
            buffer.release()
        }
    }

    @Throws(Exception::class)
    fun transformMultiBlockChange(packetWrapper: PacketWrapper) {
        val chunkX = packetWrapper.read(Type.INT)
        val chunkZ = packetWrapper.read(Type.INT)
        val size = packetWrapper.read(Type.SHORT).toInt()
        packetWrapper.read(Type.INT)
        val blocks = ShortArray(size)
        val positions = ShortArray(size)
        for (i in 0 until size) {
            positions[i] = packetWrapper.read(Type.SHORT)
            blocks[i] = packetWrapper.read(Type.SHORT)
        }
        packetWrapper.write(Type.INT, chunkX)
        packetWrapper.write(Type.INT, chunkZ)
        packetWrapper.write(
            Type.BLOCK_CHANGE_RECORD_ARRAY, IntStream.range(0, size)
                .mapToObj {
                    val encodedPos = (positions[it].toInt())
                    val x = encodedPos.ushr(12).and(0xF)
                    val y = encodedPos.and(0xFF)
                    val z = encodedPos.ushr(8).and(0xF)
                    BlockChangeRecord1_8(x, y, z, blocks[it].toInt())
                }.toList().toTypedArray()
        )
    }
}
