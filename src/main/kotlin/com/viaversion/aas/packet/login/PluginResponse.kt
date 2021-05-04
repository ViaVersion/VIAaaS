package com.viaversion.aas.packet.login

import com.viaversion.aas.packet.Packet
import com.viaversion.aas.readRemainingBytes
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBuf
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