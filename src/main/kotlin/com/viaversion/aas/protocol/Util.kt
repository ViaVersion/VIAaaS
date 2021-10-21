package com.viaversion.aas.protocol

import com.viaversion.viaversion.api.minecraft.Position
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper
import com.viaversion.viaversion.api.protocol.remapper.ValueReader
import com.viaversion.viaversion.api.protocol.remapper.ValueWriter
import com.viaversion.viaversion.api.type.Type

val xyzToPosition = ValueReader { packetWrapper: PacketWrapper ->
    val x = packetWrapper.read(Type.INT)
    val y = packetWrapper.read(Type.INT)
    val z = packetWrapper.read(Type.INT)
    Position(x, y, z)
}
val xyzUBytePos = ValueReader { packetWrapper: PacketWrapper ->
    val x = packetWrapper.read(Type.INT)
    val y = packetWrapper.read(Type.UNSIGNED_BYTE).toInt()
    val z = packetWrapper.read(Type.INT)
    Position(x, y, z)
}
val xyzUBytePosWriter = ValueWriter { packetWrapper: PacketWrapper, pos: Position ->
    packetWrapper.write(Type.INT, pos.x())
    packetWrapper.write(Type.UNSIGNED_BYTE, pos.y().toShort())
    packetWrapper.write(Type.INT, pos.z())
}
val xyzShortPosWriter = ValueWriter { packetWrapper: PacketWrapper, pos: Position ->
    packetWrapper.write(Type.INT, pos.x())
    packetWrapper.write(Type.SHORT, pos.y().toShort())
    packetWrapper.write(Type.INT, pos.z())
}
val xyzShortPos = ValueReader { packetWrapper: PacketWrapper ->
    val x = packetWrapper.read(Type.INT)
    val y = packetWrapper.read(Type.SHORT).toInt()
    val z = packetWrapper.read(Type.INT)
    Position(x, y, z)
}