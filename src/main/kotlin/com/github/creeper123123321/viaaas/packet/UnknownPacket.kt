package com.github.creeper123123321.viaaas.packet

import com.github.creeper123123321.viaaas.readableToByteArray
import io.netty.buffer.ByteBuf

class UnknownPacket(val id: Int) : Packet {
    lateinit var content: ByteArray

    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        content = readableToByteArray(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        byteBuf.writeBytes(content)
    }
}