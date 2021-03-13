package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.codec.FrameCodec
import com.github.creeper123123321.viaaas.codec.MinecraftCodec
import com.github.creeper123123321.viaaas.handler.autoprotocol.ProtocolDetectorHandler
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
        ch.pipeline()
            // "crypto"
            .addLast("frame", FrameCodec())
            // compress
            .addLast("via-codec", ViaCodec(user))
            .addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            .addLast("mc", MinecraftCodec())
            .also {
                if (connectionData.viaBackServerVer == null) {
                    it.addLast("protocol-detector", ProtocolDetectorHandler(connectionData))
                }
            }
            .addLast("handler", MinecraftHandler(connectionData, frontEnd = false))
    }
}
