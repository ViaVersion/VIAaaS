package com.github.creeper123123321.viaaas

import com.google.gson.Gson
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State

class CloudSideForwarder(val userConnection: UserConnection, var other: Channel?) : SimpleChannelInboundHandler<ByteBuf>() {

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        other?.write(msg.retain())
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        println(userConnection.channel?.remoteAddress().toString() + " was disconnected")
        other?.close()
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        super.channelReadComplete(ctx)
        other?.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        super.channelWritabilityChanged(ctx)
        other?.setAutoRead(ctx.channel().isWritable)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        disconnect("Exception: $cause")
        cause.printStackTrace()
    }

    fun disconnect(s: String) {
        if (userConnection.channel?.isActive != true) return

        val msg = "[VIAaaS] $s";
        println("Disconnecting " + userConnection.channel!!.remoteAddress() + ": " + s)
        when (userConnection.protocolInfo!!.state) {
            State.LOGIN -> {
                val packet = ByteBufAllocator.DEFAULT.buffer()
                try {
                    packet.writeByte(0) // id 0 disconnect
                    Type.STRING.write(packet, Gson().toJson(msg))
                    userConnection.sendRawPacketFuture(packet.retain()).addListener { userConnection.channel?.close() }
                } finally {
                    packet.release()
                }
            }
            State.STATUS -> {
                val packet = ByteBufAllocator.DEFAULT.buffer()
                try {
                    packet.writeByte(0) // id 0 disconnect
                    Type.STRING.write(packet, """{"version": {"name": "VIAaaS","protocol": -1},
"players": {"max": 0,"online": 0,"sample": []},	
"description": {"text": ${Gson().toJson(msg)}}}""")
                    userConnection.sendRawPacketFuture(packet.retain()).addListener { userConnection.channel?.close() }
                } finally {
                    packet.release()
                }
            }
            else -> {
                userConnection.disconnect(s)
            }
        }
    }
}