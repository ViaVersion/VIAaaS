package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.codec.FrameCodec
import com.github.creeper123123321.viaaas.codec.MinecraftCodec
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.TimeUnit

object FrontEndInit : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        ch.pipeline()
            .addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            // "crypto"
            .addLast("frame", FrameCodec())
            // "compress"
            .addLast("flow-handler", FlowControlHandler())
            .addLast("mc", MinecraftCodec())
            .addLast(
                "handler", MinecraftHandler(
                    ConnectionData(frontChannel = ch), frontEnd = true
                )
            )
    }
}