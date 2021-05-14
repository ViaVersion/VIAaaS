package com.viaversion.aas.codec.packet

import io.netty.buffer.ByteBuf

/**
 * A mutable object which represents a Minecraft packet data
 */
interface Packet {
    fun decode(byteBuf: ByteBuf, protocolVersion: Int)
    fun encode(byteBuf: ByteBuf, protocolVersion: Int)
}