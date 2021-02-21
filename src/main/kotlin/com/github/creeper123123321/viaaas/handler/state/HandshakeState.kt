package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.VIAaaSAddress
import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.mcLogger
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.packet.handshake.Handshake
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.util.concurrent.RateLimiter
import io.netty.channel.ChannelHandlerContext
import us.myles.ViaVersion.packets.State
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
        if (packet !is Handshake) throw IllegalArgumentException("Invalid packet!")

        handler.data.frontVer = packet.protocolId
        when (packet.nextState.ordinal) {
            1 -> handler.data.state = StatusState
            2 -> handler.data.state = LoginState()
            else -> throw IllegalStateException("Invalid next state")
        }

        if (!RateLimit.rateLimitByIp.get((handler.remoteAddress as InetSocketAddress).address).tryAcquire()) {
            throw IllegalStateException("Rate-limited")
        }

        val parsed = VIAaaSAddress().parse(packet.address.substringBefore(0.toChar()), VIAaaSConfig.hostName)
        val backProto = parsed.protocol
        val hadHostname = parsed.viaSuffix != null

        packet.address = parsed.serverAddress!!
        packet.port = parsed.port ?: if (VIAaaSConfig.defaultBackendPort == -1) {
            packet.port
        } else {
            VIAaaSConfig.defaultBackendPort
        }

        handler.data.viaBackServerVer = backProto
        var frontOnline = parsed.online
        if (VIAaaSConfig.forceOnlineMode) frontOnline = true

        (handler.data.state as? LoginState)?.also {
            it.frontOnline = frontOnline
            it.backName = parsed.username
            it.backAddress = packet.address to packet.port
        }

        val playerAddr = handler.data.frontHandler.remoteAddress
        mcLogger.info(
            "Handshake: ${handler.data.state.state} $playerAddr (${handler.data.frontVer}," +
                    " O: ${
                        frontOnline.toString().substring(0, 1)
                    }) -> ${packet.address}:${packet.port} (${backProto ?: "AUTO"})"
        )

        if (!hadHostname && VIAaaSConfig.requireHostName) {
            throw UnsupportedOperationException("Missing domain suffix in hostname")
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
