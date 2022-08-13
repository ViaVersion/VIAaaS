package com.viaversion.aas.handler.state

import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.mcLogger
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.ChannelHandlerContext

interface ConnectionState {
    val state: State
    fun handlePacket(
        handler: MinecraftHandler, ctx: ChannelHandlerContext,
        packet: Packet
    )
    val logDcInfo: Boolean get() = false
    val kickedByServer: Boolean get() = false

    private fun logDisconnect(handler: MinecraftHandler, msg: String?) {
        val reason = msg ?: if (handler.backEnd && kickedByServer) "kicked" else "-"
        if (logDcInfo && !handler.loggedDc) {
            handler.loggedDc = true
            mcLogger.info("- {}: {}", handler.endRemoteAddress, reason)
        } else {
            mcLogger.debug("- {}: {}", handler.endRemoteAddress, reason)
        }
    }

    fun disconnect(handler: MinecraftHandler, msg: String) {
        logDisconnect(handler, msg)
    }

    fun onInactivated(handler: MinecraftHandler) {
        logDisconnect(handler, null)
        handler.other?.close()
    }

    fun start(handler: MinecraftHandler) {
    }
}
