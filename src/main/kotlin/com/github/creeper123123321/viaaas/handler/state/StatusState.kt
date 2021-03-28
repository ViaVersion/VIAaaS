package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.UnknownPacket
import com.github.creeper123123321.viaaas.packet.status.StatusResponse
import com.github.creeper123123321.viaaas.parseProtocol
import com.github.creeper123123321.viaaas.writeFlushClose
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.packets.State
import java.util.*

object StatusState : MinecraftConnectionState {
    override val state: State
        get() = State.STATUS

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet is UnknownPacket) throw IllegalArgumentException("Invalid packet")
        when (packet) {
            is StatusResponse -> modifyResponse(handler, packet)
        }
        forward(handler, packet)
    }

    private fun modifyResponse(handler: MinecraftHandler, packet: StatusResponse) {
        if (VIAaaSConfig.showVersionPing) {
            val parsed = JsonParser.parseString(packet.json).asJsonObject
            val players = parsed.getAsJsonObject("players") ?: JsonObject().also { parsed.add("players", it) }
            val sample = players.getAsJsonArray("sample") ?: JsonArray().also { players.add("sample", it) }
            sample.add(JsonObject().also {
                it.addProperty("id", UUID.nameUUIDFromBytes("VIAaaS".toByteArray(Charsets.UTF_8)).toString())
                it.addProperty(
                    "name",
                    "§9VIAaaS§r C: §7${handler.data.frontVer!!.parseProtocol()}§r S: §7${
                        handler.data.viaBackServerVer!!.parseProtocol()
                    }"
                )
            })

            packet.json = parsed.toString()
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = StatusResponse()
        packet.json = JsonObject().also {
            it.add("version", JsonObject().also {
                it.addProperty("name", "VIAaaS")
                it.addProperty("protocol", -1)
            })
            it.add("players", JsonObject().also {
                it.addProperty("max", 0)
                it.addProperty("online", Via.getManager().connectionManager.connections.size)
                it.add("sample", JsonArray())
            })
            it.add("description", JsonObject().also {
                it.addProperty("text", "§c$msg")
            })
        }.toString()
        writeFlushClose(handler.data.frontChannel, packet)
    }
}
