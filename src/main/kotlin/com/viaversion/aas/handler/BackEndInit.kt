package com.viaversion.aas.handler

import com.viaversion.aas.codec.FrameCodec
import com.viaversion.aas.codec.MinecraftCodec
import com.viaversion.aas.handler.autoprotocol.ProtocolDetectorHandler
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.timeout.ReadTimeoutHandler
import com.viaversion.viaversion.connection.UserConnectionImpl
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl
import java.util.concurrent.TimeUnit

class BackEndInit(val connectionData: ConnectionData) : ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        val user = UserConnectionImpl(ch, true)
        ProtocolPipelineImpl(user)
        ch.pipeline().also { addSocks5(it) }
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
