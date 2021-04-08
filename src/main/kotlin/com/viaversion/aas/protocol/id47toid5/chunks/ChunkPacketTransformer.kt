package com.viaversion.aas.protocol.id47toid5.chunks

import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.minecraft.BlockChangeRecord1_8
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.api.type.types.CustomByteType
import java.io.IOException
import java.util.stream.IntStream
import java.util.zip.DataFormatException
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
        val field = PacketWrapper::class.java.getDeclaredField("inputBuffer")
        field.isAccessible = true
        val buffer = field[packetWrapper] as ByteBuf

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
        val columnCount = packetWrapper.read(Type.SHORT).toInt() //short1
        val compressedSize = packetWrapper.read(Type.INT) //size
        val skyLightSent = packetWrapper.read(Type.BOOLEAN) //h
        val chunkX = IntArray(columnCount) //a
        val chunkZ = IntArray(columnCount) //b
        val primaryBitMask = IntArray(columnCount) //c
        val addBitMask = IntArray(columnCount) //d
        val inflatedBuffers = arrayOfNulls<ByteArray>(columnCount) //inflatedBuffers
        var customByteType = CustomByteType(compressedSize)
        val buildBuffer = packetWrapper.read(customByteType) //buildBuffer
        var data = ByteArray(196864 * columnCount) //abyte
        val inflater = Inflater()
        inflater.setInput(buildBuffer, 0, compressedSize)
        try {
            inflater.inflate(data)
        } catch (ex: DataFormatException) {
            throw IOException("Bad compressed data format")
        } finally {
            inflater.end()
        }
        var cursor = 0
        for (column in 0 until columnCount) {
            chunkX[column] = packetWrapper.read(Type.INT)
            chunkZ[column] = packetWrapper.read(Type.INT)
            primaryBitMask[column] = packetWrapper.read(Type.SHORT).toInt()
            addBitMask[column] = packetWrapper.read(Type.SHORT).toInt()
            var primaryCount = 0
            var secondaryCount = 0
            (0 until 16).forEach {
                primaryCount += primaryBitMask[column] shr it and 1
                secondaryCount += addBitMask[column] shr it and 1
            }
            var copySize = 8192 * primaryCount + 256
            copySize += 2048 * secondaryCount
            if (skyLightSent) {
                copySize += 2048 * primaryCount
            }
            inflatedBuffers[column] = ByteArray(copySize)
            System.arraycopy(data, cursor, inflatedBuffers[column]!!, 0, copySize)
            cursor += copySize
        }
        val chunks = arrayOfNulls<Chunk1_8to1_7_6_10>(columnCount)
        (0 until columnCount).forEach {
            chunks[it] = Chunk1_8to1_7_6_10(inflatedBuffers[it]!!, primaryBitMask[it], addBitMask[it], skyLightSent, true)
        }
        packetWrapper.write(Type.BOOLEAN, skyLightSent)
        packetWrapper.write(Type.VAR_INT, columnCount)
        (0 until columnCount).forEach {
            packetWrapper.write(Type.INT, chunkX[it])
            packetWrapper.write(Type.INT, chunkZ[it])
            packetWrapper.write(Type.UNSIGNED_SHORT, primaryBitMask[it])
        }
        (0 until columnCount).forEach {
            data = chunks[it]!!.get1_8Data()
            customByteType = CustomByteType(data.size)
            packetWrapper.write(customByteType, data)
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
        packetWrapper.write(Type.BLOCK_CHANGE_RECORD_ARRAY, IntStream.range(0, size)
                .mapToObj {
                    val encodedPos = (positions[it].toInt())
                    val x = encodedPos.ushr(12).and(0xF)
                    val y = encodedPos.and(0xFF)
                    val z = encodedPos.ushr(8).and(0xF)
                    BlockChangeRecord1_8(x, y, z, blocks[it].toInt())
                }
                .toList().toTypedArray())
    }
}