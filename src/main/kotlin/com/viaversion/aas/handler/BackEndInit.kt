package com.viaversion.aas.handler

import com.viaversion.aas.codec.FrameCodec
import com.viaversion.aas.codec.MinecraftCodec
import com.viaversion.viaversion.connection.UserConnectionImpl
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.timeout.ReadTimeoutHandler
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class BackEndInit(val connectionData: ConnectionData, val proxyUri: URI?, val proxyAddress: InetSocketAddress?) :
    ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        val user = UserConnectionImpl(ch, true)
        ProtocolPipelineImpl(user)
        ch.pipeline().also { addProxyHandler(it, proxyUri, proxyAddress) }
            // "crypto"
            .addLast("frame", FrameCodec())
            // compress
            .addLast("via-codec", ViaCodec(user))
            .addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            .addLast("mc", MinecraftCodec())
            .addLast("handler", MinecraftHandler(connectionData, frontEnd = false))
    }
}
