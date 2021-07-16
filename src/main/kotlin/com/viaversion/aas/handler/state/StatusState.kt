package com.viaversion.aas.handler.state

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.viaversion.aas.AspirinServer
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.UnknownPacket
import com.viaversion.aas.codec.packet.status.StatusResponse
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.forward
import com.viaversion.aas.parseProtocol
import com.viaversion.aas.send
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.ChannelHandlerContext
import java.util.*

object StatusState : ConnectionState {
    override val state: State
        get() = State.STATUS

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet is UnknownPacket) throw StacklessException("Invalid packet")
        when (packet) {
            is StatusResponse -> modifyResponse(handler, packet)
        }
        forward(handler, packet)
    }

    private fun modifyResponse(handler: MinecraftHandler, packet: StatusResponse) {
        if (VIAaaSConfig.showVersionPing) {
            val parsed = JsonParser.parseString(packet.msg).asJsonObject
            val players = parsed.getAsJsonObject("players") ?: JsonObject().also { parsed.add("players", it) }
            val sample = players.getAsJsonArray("sample") ?: JsonArray().also { players.add("sample", it) }
            sample.add(JsonObject().also {
                it.addProperty("id", UUID.nameUUIDFromBytes("VIAaaS".toByteArray(Charsets.UTF_8)).toString())
                it.addProperty(
                    "name",
                    "§9VIAaaS§r C: §7${handler.data.frontVer?.parseProtocol()}§r S: §7${
                        handler.data.backServerVer?.parseProtocol()
                    }"
                )
            })

            packet.msg = parsed.toString()
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = StatusResponse()
        packet.msg = JsonObject().also {
            it.add("version", JsonObject().also {
                it.addProperty("name", "VIAaaS")
                it.addProperty("protocol", handler.data.frontVer)
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
        }.toString()
        send(handler.data.frontChannel, packet, flush = true)
        handler.data.state = StatusKicked()
    }
}
