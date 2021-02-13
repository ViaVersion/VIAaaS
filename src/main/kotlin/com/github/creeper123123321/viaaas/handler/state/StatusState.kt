package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.UnknownPacket
import com.github.creeper123123321.viaaas.packet.status.StatusResponse
import com.github.creeper123123321.viaaas.writeFlushClose
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.api.protocol.ProtocolVersion
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
                    "§9VIAaaS§r (C: §7${ProtocolVersion.getProtocol(handler.data.frontVer!!)}§r S: §7${
                        ProtocolVersion.getProtocol(handler.data.backVer!!)
                    }§r)"
                )
            })

            packet.json = parsed.toString()
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = StatusResponse()
        packet.json = """{"version": {"name": "VIAaaS", "protocol": -1}, "players": {"max": 0, "online": 0,
            | "sample": []}, "description": {"text": ${Gson().toJson("§c$msg")}}}""".trimMargin()
        writeFlushClose(handler.data.frontChannel, packet)
    }
}