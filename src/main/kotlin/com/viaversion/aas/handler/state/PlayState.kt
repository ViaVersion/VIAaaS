package com.viaversion.aas.handler.state

import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.forward
import com.viaversion.aas.handler.is1_7
import com.viaversion.aas.packet.Packet
import com.viaversion.aas.packet.UnknownPacket
import com.viaversion.aas.packet.play.Kick
import com.viaversion.aas.packet.play.PluginMessage
import com.viaversion.aas.parseProtocol
import com.viaversion.aas.readRemainingBytes
import com.viaversion.aas.writeFlushClose
import com.google.gson.JsonPrimitive
import com.viaversion.aas.util.StacklessException
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import com.viaversion.viaversion.api.type.Type
import com.viaversion.viaversion.api.protocol.packet.State

object PlayState : MinecraftConnectionState {
    override val state: State
        get() = State.PLAY

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        when {
            packet is UnknownPacket && (packet.id !in 0..127) -> throw StacklessException("Invalid packet id!")
            packet is PluginMessage && !handler.frontEnd -> modifyPluginMessage(handler, packet)
        }
        forward(handler, packet)
    }

    private fun modifyPluginMessage(handler: MinecraftHandler, pluginMessage: PluginMessage) {
        when (pluginMessage.channel) {
            "MC|Brand", "brand", "minecraft:brand" -> {
                if (!VIAaaSConfig.showBrandInfo) return
                val brand = if (is1_7(handler)) {
                    String(pluginMessage.data, Charsets.UTF_8)
                } else {
                    Type.STRING.read(Unpooled.wrappedBuffer(pluginMessage.data))
                } + " (VIAaaS C: ${handler.data.frontVer!!.parseProtocol()} S: ${
                        handler.data.viaBackServerVer!!.parseProtocol()})"

                if (is1_7(handler)) {
                    pluginMessage.data = brand.toByteArray(Charsets.UTF_8)
                } else {
                    val buf = ByteBufAllocator.DEFAULT.buffer()
                    try {
                        Type.STRING.write(buf, brand)
                        pluginMessage.data = readRemainingBytes(buf)
                    } finally {
                        buf.release()
                    }
                }
            }
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        writeFlushClose(handler.data.frontChannel,
            Kick().also { it.msg = JsonPrimitive("[VIAaaS] §c$msg").toString() })
    }
}
