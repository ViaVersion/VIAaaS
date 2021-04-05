package com.viaversion.aas.handler

import com.viaversion.aas.codec.FrameCodec
import com.viaversion.aas.codec.MinecraftCodec
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.TimeUnit

object FrontEndInit : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        ch.pipeline()
            // "crypto"
            .addLast("frame", FrameCodec())
            // "compress"
            .addLast("flow-handler", FlowControlHandler())
            .addLast("mc", MinecraftCodec())
            .addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            .addLast("handler", MinecraftHandler(ConnectionData(frontChannel = ch), frontEnd = true))
    }
}
