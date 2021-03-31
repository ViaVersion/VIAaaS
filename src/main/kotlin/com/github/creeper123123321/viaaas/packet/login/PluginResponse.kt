package com.github.creeper123123321.viaaas.packet.login

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.readRemainingBytes
import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.type.Type
import kotlin.properties.Delegates

class PluginResponse : Packet {
    var id by Delegates.notNull<Int>()
    var success by Delegates.notNull<Boolean>()
    lateinit var data: ByteArray
    override fun decode(byteBuf: ByteBuf, protocolVersion: Int) {
        id = Type.VAR_INT.readPrimitive(byteBuf)
        success = byteBuf.readBoolean()
        if (success) {
            data = readRemainingBytes(byteBuf)
        }
    }

    override fun encode(byteBuf: ByteBuf, protocolVersion: Int) {
        Type.VAR_INT.writePrimitive(byteBuf, id)
        byteBuf.writeBoolean(success)
        if (success) {
            byteBuf.writeBytes(data)
        }
    }
}