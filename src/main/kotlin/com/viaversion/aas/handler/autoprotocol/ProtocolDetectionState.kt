package com.viaversion.aas.handler.autoprotocol

import com.google.gson.JsonParser
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.status.StatusResponse
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.state.ConnectionState
import com.viaversion.aas.mcLogger
import com.viaversion.aas.parseProtocol
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.netty.channel.ChannelHandlerContext
import java.util.concurrent.CompletableFuture

class ProtocolDetectionState(val future: CompletableFuture<ProtocolVersion>) : ConnectionState {
    override val state: State
        get() = State.STATUS

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        handler.data.frontChannel.close()
        if (packet !is StatusResponse) throw StacklessException("Unexpected packet")
        val ver = JsonParser.parseString(packet.msg).asJsonObject
            .getAsJsonObject("version")["protocol"].asInt.parseProtocol()
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
