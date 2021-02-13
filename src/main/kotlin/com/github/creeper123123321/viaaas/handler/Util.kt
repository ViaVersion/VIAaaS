package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.send
import us.myles.ViaVersion.api.protocol.ProtocolVersion

fun forward(handler: MinecraftHandler, packet: Packet, flush: Boolean = false) {
    send(handler.other!!, packet, flush)
}

fun is1_7(handler: MinecraftHandler) = handler.data.frontVer!! <= ProtocolVersion.v1_7_6.version