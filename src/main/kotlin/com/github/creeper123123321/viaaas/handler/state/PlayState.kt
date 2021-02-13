package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import com.github.creeper123123321.viaaas.handler.is1_7
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.UnknownPacket
import com.github.creeper123123321.viaaas.packet.play.PluginMessage
import com.github.creeper123123321.viaaas.readableToByteArray
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State

object PlayState : MinecraftConnectionState {
    override val state: State
        get() = State.PLAY

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        when {
            packet is UnknownPacket && (packet.id !in 0..127) -> throw IllegalArgumentException("Invalid packet id!")
            packet is PluginMessage -> modifyPluginMessage(handler, packet)
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
                } + " (VIAaaS C: ${ProtocolVersion.getProtocol(handler.data.frontVer!!)} S: ${
                    ProtocolVersion.getProtocol(
                        handler.data.backVer!!
                    )
                })"

                if (is1_7(handler)) {
                    pluginMessage.data = brand.toByteArray(Charsets.UTF_8)
                } else {
                    val buf = ByteBufAllocator.DEFAULT.buffer()
                    try {
                        Type.STRING.write(buf, brand)
                        pluginMessage.data = readableToByteArray(buf)
                    } finally {
                        buf.release()
                    }
                }
            }
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close()
    }
}