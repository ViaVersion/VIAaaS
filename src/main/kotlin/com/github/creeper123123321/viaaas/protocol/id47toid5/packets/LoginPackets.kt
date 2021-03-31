package com.github.creeper123123321.viaaas.protocol.id47toid5.packets

import com.github.creeper123123321.viaaas.protocol.id47toid5.Protocol1_8To1_7_6
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.api.type.types.CustomByteType
import us.myles.ViaVersion.packets.State

fun Protocol1_8To1_7_6.registerLoginPackets() {
    //Encryption Request
    this.registerOutgoing(State.LOGIN, 0x01, 0x01, object : PacketRemapper() {
        override fun registerMap() {
            map(Type.STRING) //Server ID
            handler { packetWrapper ->
                val publicKeyLength = packetWrapper.read(Type.SHORT).toInt()
                packetWrapper.write(Type.VAR_INT, publicKeyLength)
                packetWrapper.passthrough(CustomByteType(publicKeyLength))
                val verifyTokenLength = packetWrapper.read(Type.SHORT).toInt()
                packetWrapper.write(Type.VAR_INT, verifyTokenLength)
                packetWrapper.passthrough(CustomByteType(verifyTokenLength))
            }
        }
    })

    //Encryption Response
    this.registerIncoming(State.LOGIN, 0x01, 0x01, object : PacketRemapper() {
        override fun registerMap() {
            handler { packetWrapper ->
                val sharedSecretLength: Int = packetWrapper.read(Type.VAR_INT)
                packetWrapper.write(Type.SHORT, sharedSecretLength.toShort())
                packetWrapper.passthrough(CustomByteType(sharedSecretLength))
                val verifyTokenLength: Int = packetWrapper.read(Type.VAR_INT)
                packetWrapper.write(Type.SHORT, verifyTokenLength.toShort())
                packetWrapper.passthrough(CustomByteType(verifyTokenLength))
            }
        }
    })
}