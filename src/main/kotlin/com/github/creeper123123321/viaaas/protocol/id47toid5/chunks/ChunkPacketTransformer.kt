package com.github.creeper123123321.viaaas.protocol.id47toid5.chunks

import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.minecraft.BlockChangeRecord1_8
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.api.type.types.CustomByteType
import java.io.IOException
import java.util.*
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
        var k = 0
        var l = 0
        for (j in 0..15) {
            k += primaryBitMask shr j and 1
            l += addBitMask shr j and 1
        }
        var uncompressedSize = 12288 * k
        uncompressedSize += 2048 * l
        if (groundUp) {
            uncompressedSize += 256
        }
        val uncompressedData = ByteArray(uncompressedSize)
        val inflater = Inflater()
        inflater.setInput(data, 0, compressedSize)
        try {
            inflater.inflate(uncompressedData)
        } catch (ex: DataFormatException) {
            throw IOException("Bad compressed data format")
        } finally {
            inflater.end()
        }
        val chunk = Chunk1_8to1_7_6_10(uncompressedData, primaryBitMask, addBitMask, true, groundUp)
        var field = PacketWrapper::class.java.getDeclaredField("packetValues")
        field.isAccessible = true
        (field[packetWrapper] as MutableList<*>).clear()
        field = PacketWrapper::class.java.getDeclaredField("readableObjects")
        field.isAccessible = true
        (field[packetWrapper] as LinkedList<*>).clear()
        field = PacketWrapper::class.java.getDeclaredField("inputBuffer")
        field.isAccessible = true
        val buffer = field[packetWrapper] as ByteBuf
        buffer.clear()
        buffer.writeInt(chunkX)
        buffer.writeInt(chunkZ)
        buffer.writeBoolean(groundUp)
        buffer.writeShort(primaryBitMask)
        val finaldata = chunk.get1_8Data()
        Type.VAR_INT.writePrimitive(buffer, finaldata.size)
        buffer.writeBytes(finaldata)
    }

    @Throws(Exception::class)
    fun transformChunkBulk(packetWrapper: PacketWrapper) {
        val columnCount = packetWrapper.read(Type.SHORT).toInt() //short1
        val size = packetWrapper.read(Type.INT) //size
        val skyLightSent = packetWrapper.read(Type.BOOLEAN) //h
        val chunkX = IntArray(columnCount) //a
        val chunkZ = IntArray(columnCount) //b
        val primaryBitMask = IntArray(columnCount) //c
        val addBitMask = IntArray(columnCount) //d
        val inflatedBuffers = arrayOfNulls<ByteArray>(columnCount.toInt()) //inflatedBuffers
        var customByteType = CustomByteType(size)
        val buildBuffer = packetWrapper.read(customByteType) //buildBuffer
        var data = ByteArray(196864 * columnCount) //abyte
        val inflater = Inflater()
        inflater.setInput(buildBuffer, 0, size)
        try {
            inflater.inflate(data)
        } catch (ex: DataFormatException) {
            throw IOException("Bad compressed data format")
        } finally {
            inflater.end()
        }
        var i = 0
        for (j in 0 until columnCount) {
            chunkX[j] = packetWrapper.read(Type.INT)
            chunkZ[j] = packetWrapper.read(Type.INT)
            primaryBitMask[j] = packetWrapper.read(Type.SHORT).toInt()
            addBitMask[j] = packetWrapper.read(Type.SHORT).toInt()
            var k = 0
            var l = 0
            var i1: Int
            i1 = 0
            while (i1 < 16) {
                k += primaryBitMask[j] shr i1 and 1
                l += addBitMask[j] shr i1 and 1
                ++i1
            }
            i1 = 8192 * k + 256
            i1 += 2048 * l
            if (skyLightSent) {
                i1 += 2048 * k
            }
            inflatedBuffers[j] = ByteArray(i1)
            System.arraycopy(data, i, inflatedBuffers[j], 0, i1)
            i += i1
        }
        val chunks = arrayOfNulls<Chunk1_8to1_7_6_10>(columnCount.toInt())
        i = 0
        while (i < columnCount) {
            chunks[i] = Chunk1_8to1_7_6_10(inflatedBuffers[i], primaryBitMask[i], addBitMask[i], skyLightSent, true)
            i++
        }
        packetWrapper.write(Type.BOOLEAN, skyLightSent)
        packetWrapper.write(Type.VAR_INT, columnCount)
        i = 0
        while (i < columnCount) {
            packetWrapper.write(Type.INT, chunkX[i])
            packetWrapper.write(Type.INT, chunkZ[i])
            packetWrapper.write(Type.UNSIGNED_SHORT, primaryBitMask[i])
            ++i
        }
        i = 0
        while (i < columnCount) {
            data = chunks[i]!!.get1_8Data()
            customByteType = CustomByteType(data.size)
            packetWrapper.write(customByteType, data)
            ++i
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