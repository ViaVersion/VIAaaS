package com.viaversion.aas.codec.packet

import com.viaversion.aas.readRemainingBytes
import io.netty.buffer.ByteBuf

class UnknownPacket(val id: Int) : Packet {
    lateinit var content: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        content = readRemainingBytes(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        byteBuf.writeBytes(content)
    }
}