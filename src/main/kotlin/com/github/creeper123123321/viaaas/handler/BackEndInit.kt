package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.codec.FrameCodec
import com.github.creeper123123321.viaaas.codec.MinecraftCodec
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.timeout.ReadTimeoutHandler
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.ProtocolPipeline
import java.util.concurrent.TimeUnit

class BackEndInit(val connectionData: ConnectionData) : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        val user = UserConnection(ch, true)
        ProtocolPipeline(user)
        ch.pipeline().addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            // "crypto"
            .addLast("frame", FrameCodec())
            // compress
            .addLast("via-codec", ViaCodec(user))
            .addLast("mc", MinecraftCodec())
            .addLast("handler", MinecraftHandler(connectionData, frontEnd = false))
    }
}