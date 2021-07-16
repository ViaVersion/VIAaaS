package com.viaversion.aas.handler.state

import com.google.gson.JsonPrimitive
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.UnknownPacket
import com.viaversion.aas.codec.packet.play.Kick
import com.viaversion.aas.codec.packet.play.PluginMessage
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.*
import com.viaversion.aas.parseProtocol
import com.viaversion.aas.util.StacklessException
import com.viaversion.aas.writeFlushClose
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.ChannelHandlerContext
import io.netty.util.ReferenceCountUtil

object PlayState : ConnectionState {
    override val state: State
        get() = State.PLAY
    override val logDcInfo: Boolean
        get() = true

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        when {
            packet is UnknownPacket && (packet.id !in 0..127) -> throw StacklessException("Invalid packet id!")
            packet is PluginMessage && !handler.frontEnd -> modifyPluginMessage(handler, packet)
        }
        forward(handler, ReferenceCountUtil.retain(packet))
    }

    private fun modifyPluginMessage(handler: MinecraftHandler, pluginMessage: PluginMessage) {
        when (pluginMessage.channel) {
            "MC|Brand", "brand", "minecraft:brand" -> {
                if (!VIAaaSConfig.showBrandInfo) return

                val brand = "${
                    decodeBrand(
                        pluginMessage.data,
                        is17(handler)
                    )
                }${" (VIAaaS C: ${handler.data.frontVer?.parseProtocol()} S: ${handler.data.backServerVer?.parseProtocol()})"}"

                pluginMessage.data = encodeBrand(brand, is17(handler))
            }
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        writeFlushClose(
            handler.data.frontChannel,
            Kick().also { it.msg = JsonPrimitive("[VIAaaS] §c$msg").toString() },
            delay = is17(handler)
        )
    }
}
