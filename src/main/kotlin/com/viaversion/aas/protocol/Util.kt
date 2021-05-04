package com.viaversion.aas.protocol

import com.viaversion.viaversion.api.minecraft.Position
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.remapper.ValueReader
import com.viaversion.viaversion.api.protocol.remapper.ValueTransformer
import com.viaversion.viaversion.api.protocol.remapper.ValueWriter
import com.viaversion.viaversion.api.type.Type

val INSERT_DASHES: ValueTransformer<String, String> = object : ValueTransformer<String, String>(Type.STRING) {
    override fun transform(packetWrapper: PacketWrapper, s: String?): String {
        val builder = StringBuilder(s)
        builder.insert(20, "-")
        builder.insert(16, "-")
        builder.insert(12, "-")
        builder.insert(8, "-")
        return builder.toString()
    }
}

val xyzToPosition = ValueReader { packetWrapper: PacketWrapper ->
    val x = packetWrapper.read(Type.INT)
    val y = packetWrapper.read(Type.INT).toShort()
    val z = packetWrapper.read(Type.INT)
    Position(x, y, z)
}
val xyzUBytePos = ValueReader { packetWrapper: PacketWrapper ->
    val x = packetWrapper.read(Type.INT)
    val y = packetWrapper.read(Type.UNSIGNED_BYTE)
    val z = packetWrapper.read(Type.INT)
    Position(x, y, z)
}
val xyzUBytePosWriter: ValueWriter<Position> = ValueWriter<Position> { packetWrapper: PacketWrapper, pos: Position ->
    packetWrapper.write(Type.INT, pos.x)
    packetWrapper.write(Type.UNSIGNED_BYTE, pos.y.toShort())
    packetWrapper.write(Type.INT, pos.z)
}
val xyzShortPosWriter: ValueWriter<Position> = ValueWriter<Position> { packetWrapper: PacketWrapper, pos: Position ->
    packetWrapper.write(Type.INT, pos.x)
    packetWrapper.write(Type.SHORT, pos.y.toShort())
    packetWrapper.write(Type.INT, pos.z)
}
val xyzShortPos: ValueReader<Position> = ValueReader<Position> { packetWrapper: PacketWrapper ->
    val x = packetWrapper.read(Type.INT)
    val y = packetWrapper.read(Type.SHORT)
    val z = packetWrapper.read(Type.INT)
    Position(x, y, z)
}