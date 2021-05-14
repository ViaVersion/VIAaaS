package com.viaversion.aas.handler.autoprotocol

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.viaversion.aas.channelSocketFactory
import com.viaversion.aas.childLoop
import com.viaversion.aas.codec.FrameCodec
import com.viaversion.aas.codec.MinecraftCodec
import com.viaversion.aas.handler.ConnectionData
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.addSocks5
import com.viaversion.aas.mcLogger
import com.viaversion.aas.codec.packet.handshake.Handshake
import com.viaversion.aas.codec.packet.status.StatusRequest
import com.viaversion.aas.send
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.resolver.NoopAddressResolverGroup
import io.netty.util.concurrent.Future
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

object ProtocolDetector {
    private val SERVER_VER = CacheBuilder.newBuilder()
        .expireAfterAccess(100, TimeUnit.SECONDS)
        .build(CacheLoader.from { address: InetSocketAddress? ->
            val future = CompletableFuture<ProtocolVersion>()
            try {
                val ch: ChannelFuture = Bootstrap()
                    .group(childLoop)
                    .resolver(NoopAddressResolverGroup.INSTANCE)
                    .channelFactory(channelSocketFactory(childLoop))
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.IP_TOS, 0x18)
                    .handler(object : ChannelInitializer<Channel>() {
                        override fun initChannel(channel: Channel) {
                            val data = ConnectionData(channel, state = ProtocolDetectionState(future), frontVer = -1)
                            channel.pipeline().also { addSocks5(it) }
                                .addLast("timeout", ReadTimeoutHandler(30, TimeUnit.SECONDS))
                                .addLast("frame", FrameCodec())
                                .addLast("mc", MinecraftCodec())
                                .addLast("handler", MinecraftHandler(data, frontEnd = false))
                        }
                    })
                    .connect(address!!)
                ch.addListener { future1: Future<in Void> ->
                    if (!future1.isSuccess) {
                        future.completeExceptionally(future1.cause())
                    } else {
                        ch.channel().eventLoop().execute {
                            val handshake = Handshake()
                            handshake.address = address.hostString
                            handshake.port = address.port
                            handshake.protocolId = -1
                            handshake.nextState = State.STATUS
                            send(ch.channel(), handshake)
                            send(ch.channel(), StatusRequest(), flush = true)
                        }
                    }
                }
            } catch (throwable: Throwable) {
                future.completeExceptionally(throwable)
            }
            future
        })

    fun detectVersion(address: InetSocketAddress): CompletableFuture<ProtocolVersion> {
        return try {
            SERVER_VER[address]
        } catch (e: ExecutionException) {
            mcLogger.warn("Protocol auto detector error: ", e)
            CompletableFuture.completedFuture(null)
        }
    }
}
