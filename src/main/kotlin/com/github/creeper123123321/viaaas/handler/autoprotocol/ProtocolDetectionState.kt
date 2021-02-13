package com.github.creeper123123321.viaaas.handler.autoprotocol

import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.state.MinecraftConnectionState
import com.github.creeper123123321.viaaas.mcLogger
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.status.StatusResponse
import com.google.gson.JsonParser
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.packets.State
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CompletableFuture

class ProtocolDetectionState(val future: CompletableFuture<ProtocolVersion>) : MinecraftConnectionState {
    override val state: State
        get() = State.STATUS

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        handler.data.frontChannel.close()
        if (packet !is StatusResponse) throw IllegalArgumentException()
        val ver = ProtocolVersion.getProtocol(
            JsonParser.parseString(packet.json).asJsonObject.getAsJsonObject("version").get("protocol").asInt
        )
        future.complete(ver)
        mcLogger.info("Auto-detected $ver for ${handler.remoteAddress}")
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close()
    }

    override fun onInactivated(handler: MinecraftHandler) {
        future.completeExceptionally(ClosedChannelException())
    }
}