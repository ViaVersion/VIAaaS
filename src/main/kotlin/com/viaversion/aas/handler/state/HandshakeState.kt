package com.viaversion.aas.handler.state

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.RateLimiter
import com.viaversion.aas.VIAaaSAddress
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.mcLogger
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.handshake.Handshake
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.ChannelHandlerContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class HandshakeState : MinecraftConnectionState {
    object RateLimit {
        val rateLimitByIp = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build(CacheLoader.from<InetAddress, RateLimiter> {
                RateLimiter.create(VIAaaSConfig.rateLimitConnectionMc)
            })
    }

    override val state: State
        get() = State.HANDSHAKE

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet !is Handshake) throw StacklessException("Invalid packet!")

        handler.data.frontVer = packet.protocolId
        when (packet.nextState.ordinal) {
            1 -> handler.data.state = StatusState
            2 -> handler.data.state = LoginState()
            else -> throw StacklessException("Invalid next state")
        }

        if (!RateLimit.rateLimitByIp.get((handler.endRemoteAddress as InetSocketAddress).address).tryAcquire()) {
            throw StacklessException("Rate-limited")
        }
        val virtualPort = packet.port
        val extraData = packet.address.substringAfter(0.toChar(), missingDelimiterValue = "").ifEmpty { null } // todo
        val virtualHostNoExtra = packet.address.substringBefore(0.toChar())

        val parsed = VIAaaSConfig.hostName.map {
            VIAaaSAddress().parse(virtualHostNoExtra, it)
        }.sortedBy {
            it.viaSuffix == null
        }.first()

        val backProto = parsed.protocol
        val hadHostname = parsed.viaSuffix != null

        packet.address = parsed.serverAddress!!
        packet.port = parsed.port ?: VIAaaSConfig.defaultBackendPort ?: virtualPort

        handler.data.viaBackServerVer = backProto
        var frontOnline = parsed.online
        if (VIAaaSConfig.forceOnlineMode) frontOnline = true

        (handler.data.state as? LoginState)?.also {
            it.frontOnline = frontOnline
            it.backName = parsed.username
            it.backAddress = HostAndPort.fromParts(packet.address, packet.port)
        }

        val playerAddr = handler.data.frontHandler.endRemoteAddress
        mcLogger.info(
            "HS: $playerAddr ${handler.data.state.state.toString().substring(0, 1)} " +
                    "$virtualHostNoExtra $virtualPort v${handler.data.frontVer}"
        )

        if (!hadHostname && VIAaaSConfig.requireHostName) {
            throw StacklessException("Missing parts in hostname")
        }

        if (packet.nextState == State.STATUS) {
            connectBack(handler, packet.address, packet.port, packet.nextState) {}
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        handler.data.frontChannel.close() // Not worth logging
    }

    override fun onInactivated(handler: MinecraftHandler) {
        // Not worth logging
    }
}
