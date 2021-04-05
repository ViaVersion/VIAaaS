package com.viaversion.aas.packet.status

import com.viaversion.aas.packet.Packet
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.type.Type

class StatusResponse : Packet {
    lateinit var json: String
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        json = Type.STRING.read(byteBuf)
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.STRING.write(byteBuf, json)
    }
}