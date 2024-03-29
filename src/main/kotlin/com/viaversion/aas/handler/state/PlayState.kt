package com.viaversion.aas.handler.state

import com.google.gson.JsonPrimitive
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.UnknownPacket
import com.viaversion.aas.codec.packet.play.ConfigurationAck
import com.viaversion.aas.codec.packet.play.Kick
import com.viaversion.aas.codec.packet.play.PluginMessage
import com.viaversion.aas.codec.packet.play.ServerboundChatCommand
import com.viaversion.aas.codec.packet.play.ServerboundChatMessage
import com.viaversion.aas.codec.packet.play.SetPlayCompression
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.*
import com.viaversion.aas.mcLogger
import com.viaversion.aas.util.StacklessException
import com.viaversion.aas.writeFlushClose
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.ChannelHandlerContext
import io.netty.util.ReferenceCountUtil

class PlayState : ConnectionState {
    override val state: State
        get() = State.PLAY
    override val logDcInfo: Boolean
        get() = true
    override var kickedByServer = false

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        when {
            packet is UnknownPacket && (packet.id !in 0..127) -> throw StacklessException("Invalid packet id!")
            packet is PluginMessage -> modifyPluginMessage(handler, packet)
            packet is SetPlayCompression -> return handleCompression(handler, packet)
            packet is Kick -> handleKick(handler, packet)
            packet is ServerboundChatCommand -> modifyChatCommand(packet)
            packet is ServerboundChatMessage -> modifyChatMessage(packet)
            packet is ConfigurationAck -> handleConfigAck(handler, packet)
        }
        forward(handler, ReferenceCountUtil.retain(packet))
    }

    private fun handleConfigAck(handler: MinecraftHandler, packet: ConfigurationAck) {
        handler.data.state = ConfigurationState()
    }

    private fun handleKick(handler: MinecraftHandler, packet: Kick) {
        kickedByServer = true
        mcLogger.debug(
            "{} disconnected on play: {}",
            handler.endRemoteAddress.toString(),
            packet.msgAsJson
        )
    }

    private fun handleCompression(handler: MinecraftHandler, packet: SetPlayCompression) {
        val threshold = packet.threshold

        setCompression(handler.data.backChannel!!, threshold)

        forward(handler, packet)

        setCompression(handler.data.frontChannel, threshold)
    }

    private fun modifyPluginMessage(handler: MinecraftHandler, pluginMessage: PluginMessage) {
        if (handler.frontEnd) return
        try {
            when (pluginMessage.channel) {
                "MC|Brand", "brand", "minecraft:brand" -> {
                    if (!VIAaaSConfig.showBrandInfo) return

                    val brand = "${
                        decodeBrand(
                            pluginMessage.data,
                            is17(handler)
                        )
                    }${" (VIAaaS C: ${handler.data.frontVer} S: ${handler.data.backServerVer})"}"

                    pluginMessage.data = encodeBrand(brand, is17(handler))
                }
            }
        } catch (e: Exception) {
            mcLogger.debug("Couldn't modify plugin message", e)
        }
    }

    private fun modifyChatCommand(chatCommand: ServerboundChatCommand) {
        // todo handle signatures?
        chatCommand.isSignedPreview = false
        chatCommand.signatures = emptyArray()
    }

    private fun modifyChatMessage(chatMessage: ServerboundChatMessage) {
        chatMessage.signature = byteArrayOf()
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        writeFlushClose(
            handler.data.frontChannel,
            Kick().also { it.setMsgForVersion(JsonPrimitive("[VIAaaS] §c$msg"), handler.data.frontVer!!) },
            delay = is17(handler)
        )
    }
}
