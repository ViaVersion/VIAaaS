package com.github.creeper123123321.viaaas.packet.status

import com.github.creeper123123321.viaaas.packet.Packet
import io.netty.buffer.ByteBuf

class StatusRequest: Packet {
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
    }
}