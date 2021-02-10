package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.send


fun forward(handler: CloudMinecraftHandler, packet: Packet, flush: Boolean = false) {
    send(handler.other!!, packet, flush)
}