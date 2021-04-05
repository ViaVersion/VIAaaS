package com.viaversion.aas.handler.autoprotocol

import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.state.MinecraftConnectionState
import com.viaversion.aas.mcLogger
import com.viaversion.aas.packet.Packet
import com.viaversion.aas.packet.status.StatusResponse
import com.viaversion.aas.parseProtocol
import com.google.gson.JsonParser
import com.viaversion.aas.util.StacklessException
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
        if (packet !is StatusResponse) throw StacklessException("unexpected packet")
        val ver = JsonParser.parseString(packet.json).asJsonObject
                .getAsJsonObject("version").get("protocol").asInt.parseProtocol()
        future.complete(ver)
        mcLogger.info("A.D.: ${handler.endRemoteAddress} $ver")
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close()
    }

    override fun onInactivated(handler: MinecraftHandler) {
        future.completeExceptionally(StacklessException("closed"))
    }
}
