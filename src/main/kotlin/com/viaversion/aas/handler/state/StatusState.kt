package com.viaversion.aas.handler.state

import com.google.common.net.HostAndPort
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.viaversion.aas.*
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.UnknownPacket
import com.viaversion.aas.codec.packet.status.StatusResponse
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.forward
import com.viaversion.aas.util.IntendedState
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class StatusState : ConnectionState {
    override val state: State
        get() = State.STATUS
    var address: HostAndPort? = null

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet is UnknownPacket) throw StacklessException("Invalid packet")
        when (packet) {
            is StatusResponse -> modifyResponse(handler, packet)
        }
        forward(handler, packet)
    }

    private fun modifyResponse(handler: MinecraftHandler, packet: StatusResponse) {
        if (VIAaaSConfig.showVersionPing) {
            val parsed = packet.msg.asJsonObject
            val players = parsed.getAsJsonObject("players") ?: JsonObject().also { parsed.add("players", it) }
            val sample = players.getAsJsonArray("sample") ?: JsonArray().also { players.add("sample", it) }
            sample.add(JsonObject().also {
                it.addProperty("id", UUID.nameUUIDFromBytes("VIAaaS".toByteArray(Charsets.UTF_8)).toString())
                it.addProperty(
                    "name", "§9VIAaaS§r C: §7%s§'r S: §7%s"
                        .format(handler.data.frontVer, handler.data.backServerVer)
                )
            })
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = StatusResponse()
        packet.msg = JsonObject().also {
            it.add("version", JsonObject().also {
                it.addProperty("name", "VIAaaS")
                it.addProperty("protocol", handler.data.frontVer!!.version)
            })
            it.add("players", JsonObject().also {
                it.addProperty("max", VIAaaSConfig.maxPlayers ?: -1)
                it.addProperty("online", AspirinServer.currentPlayers())
                it.add("sample", JsonArray())
            })
            it.add("description", JsonObject().also {
                it.addProperty("text", "§c$msg")
            })
            VIAaaSConfig.faviconUrl?.let { favicon ->
                it.addProperty("favicon", favicon)
            }
        }
        send(handler.data.frontChannel, packet, flush = true)
        handler.data.state = StatusKicked()
        handler.data.state.start(handler)
    }

    override fun start(handler: MinecraftHandler) {
        handler.data.frontChannel.setAutoRead(false)
        handler.coroutineScope.launch(Dispatchers.IO) {
            try {
                if (address != null) {
                    connectBack(handler, address!!, IntendedState.STATUS)
                } else {
                    handler.disconnect("VIAaaS")
                }
            } catch (e: Exception) {
                handler.data.frontChannel.fireExceptionCaughtIfOpen(e)
            }
        }
    }
}
