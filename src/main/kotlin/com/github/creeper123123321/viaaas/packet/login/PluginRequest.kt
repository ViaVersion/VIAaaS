package com.github.creeper123123321.viaaas.packet.login

import com.github.creeper123123321.viaaas.packet.Packet
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.type.Type
import kotlin.properties.Delegates

class PluginRequest : Packet {
    var id by Delegates.notNull<Int>()
    lateinit var channel: String
    lateinit var data: ByteArray
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        id = Type.VAR_INT.readPrimitive(byteBuf)
        channel = Type.STRING.read(byteBuf)
        data = ByteArray(byteBuf.readableBytes()).also { byteBuf.readBytes(it) }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, id)
        Type.STRING.write(byteBuf, channel)
        byteBuf.writeBytes(data)
    }
}