package com.viaversion.aas.protocol.id47toid5.packets

import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State

fun Protocol1_8To1_7_6.registerLoginPackets() {
    //Encryption Request
    this.registerOutgoing(State.LOGIN, 0x01, 0x01, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.STRING) //Server ID
            map(Type.SHORT_BYTE_ARRAY, Type.BYTE_ARRAY_PRIMITIVE)
            map(Type.SHORT_BYTE_ARRAY, Type.BYTE_ARRAY_PRIMITIVE)
        }
    })

    //Encryption Response
    this.registerIncoming(State.LOGIN, 0x01, 0x01, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.BYTE_ARRAY_PRIMITIVE, Type.SHORT_BYTE_ARRAY)
            map(Type.BYTE_ARRAY_PRIMITIVE, Type.SHORT_BYTE_ARRAY)
        }
    })
}