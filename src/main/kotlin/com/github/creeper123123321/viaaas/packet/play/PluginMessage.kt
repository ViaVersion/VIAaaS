package com.github.creeper123123321.viaaas.packet.play

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.readRemainingBytes
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type

class PluginMessage : Packet {
    lateinit var channel: String
    lateinit var data: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        channel = Type.STRING.read(byteBuf)
        data = if (protocolVersion <= ProtocolVersion.v1_7_6.version) {
            ByteArray(readExtendedForgeShort(byteBuf)).also { byteBuf.readBytes(it) }
        } else {
            readRemainingBytes(byteBuf)
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, channel)
        if (protocolVersion <= ProtocolVersion.v1_7_6.version) {
            writeExtendedForgeShort(byteBuf, data.size)
        }
        byteBuf.writeBytes(data)
    }

    // stolen from https://github.com/VelocityPowered/Velocity/blob/27ccb9d387fc9a0aecd5c4b570d7d957558efddc/proxy/src/main/java/com/velocitypowered/proxy/protocol/ProtocolUtils.java#L418
    fun readExtendedForgeShort(buf: ByteBuf): Int {
        var low = buf.readUnsignedShort()
        var high = 0
        if (low and 0x8000 != 0) {
            low = low and 0x7FFF
            high = buf.readUnsignedByte().toInt()
        }
        return high and 0xFF shl 15 or low
    }

    fun writeExtendedForgeShort(buf: ByteBuf, toWrite: Int) {
        var low = toWrite and 0x7FFF
        val high = toWrite and 0x7F8000 shr 15
        if (high != 0) {
            low = low or 0x8000
        }
        buf.writeShort(low)
        if (high != 0) {
            buf.writeByte(high)
        }
    }
}