package com.viaversion.aas.codec.packet.status

import com.viaversion.aas.codec.packet.Packet
import io.netty.buffer.ByteBuf

class StatusRequest : Packet {
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
    }
}