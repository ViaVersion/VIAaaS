package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.status.StatusResponse
import com.github.creeper123123321.viaaas.packet.UnknownPacket
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import com.github.creeper123123321.viaaas.writeFlushClose
import com.google.gson.Gson
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.packets.State

object StatusState : MinecraftConnectionState {
    override val state: State
        get() = State.STATUS

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet is UnknownPacket) throw IllegalArgumentException("Invalid packet")
        forward(handler, packet)
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = StatusResponse()
        packet.json = """{"version": {"name": "VIAaaS", "protocol": -1}, "players": {"max": 0, "online": 0,
            | "sample": []}, "description": {"text": ${Gson().toJson("§c$msg")}}}""".trimMargin()
        writeFlushClose(handler.data.frontChannel, packet)
    }
}