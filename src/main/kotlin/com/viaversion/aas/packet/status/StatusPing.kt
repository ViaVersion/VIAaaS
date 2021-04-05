package com.viaversion.aas.packet.status

import com.viaversion.aas.packet.Packet
import io.netty.buffer.ByteBuf
import kotlin.properties.Delegates

class StatusPing : Packet {
    var number by Delegates.notNull<Long>()
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        number = byteBuf.readLong()
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        byteBuf.writeLong(number)
    }
}