package com.viaversion.aas.handler

import com.viaversion.aas.codec.FrameCodec
import com.viaversion.aas.codec.MinecraftCodec
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.connection.UserConnectionImpl
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl
import io.netty.channel.Channel
import io.netty.channel.ChannelInitializer
import io.netty.handler.timeout.ReadTimeoutHandler
import net.raphimc.vialegacy.api.LegacyProtocolVersion
import net.raphimc.vialegacy.netty.PreNettyLengthCodec
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.baseprotocols.PreNettyBaseProtocol
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

class BackEndInit(val connectionData: ConnectionData, val proxyUri: URI?, val proxyAddress: InetSocketAddress?) :
    ChannelInitializer<Channel>() {
    override fun initChannel(ch: Channel) {
        val user = UserConnectionImpl(ch, true)
        val pipeline = ProtocolPipelineImpl(user)
        val version = connectionData.backServerVer!!
        val isLegacy = LegacyProtocolVersion.protocolCompare(version, LegacyProtocolVersion.r1_6_4.version) <= 0

        if (isLegacy) {
            pipeline.add(PreNettyBaseProtocol.INSTANCE)
        }

        ch.pipeline()
            .also { addProxyHandler(it, proxyUri, proxyAddress) }
            .also { if (isLegacy) it.addLast("vl-prenetty", PreNettyLengthCodec(user)) }
            // "crypto"
            .addLast("frame", FrameCodec())
            // compress
            .addLast("via-codec", ViaCodec(user))
            .addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
            .addLast("mc", MinecraftCodec())
            .addLast("handler", MinecraftHandler(connectionData, frontEnd = false))
    }
}
