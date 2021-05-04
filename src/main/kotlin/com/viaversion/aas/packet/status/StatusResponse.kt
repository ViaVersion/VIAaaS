package com.viaversion.aas.packet.status

import com.viaversion.aas.packet.Packet
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf

class StatusResponse : Packet {
    lateinit var json: String
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        json = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, json)
    }
}