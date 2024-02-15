package com.viaversion.aas.handler.state

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.RateLimiter
import com.viaversion.aas.AUTO
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.handshake.Handshake
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.mcLogger
import com.viaversion.aas.util.AddressParser
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.netty.channel.ChannelHandlerContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

class HandshakeState : ConnectionState {
    object RateLimit {
        val rateLimitByIp = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.MINUTES)
            .build(CacheLoader.from<InetAddress, Limits> {
                Limits(
                    RateLimiter.create(VIAaaSConfig.rateLimitConnectionMc),
                    RateLimiter.create(VIAaaSConfig.rateLimitLoginMc)
                )
            })

        data class Limits(val handshakeLimiter: RateLimiter, val loginLimiter: RateLimiter)
    }

    override val state: State
        get() = State.HANDSHAKE

    private fun checkRateLimit(handler: MinecraftHandler, state: State) {
        val socketAddress = (handler.endRemoteAddress as InetSocketAddress).address
        val limit = RateLimit.rateLimitByIp[socketAddress]

        if (!limit.handshakeLimiter.tryAcquire()
            || (state == State.LOGIN && !limit.loginLimiter.tryAcquire())
        ) {
            throw StacklessException("Rate-limited")
        }
    }

    private fun handleNextState(handler: MinecraftHandler, packet: Handshake) {
        handler.data.frontVer = ProtocolVersion.getProtocol(packet.protocolId)
        when (packet.nextState.ordinal) {
            1 -> handler.data.state = StatusState()
            2 -> handler.data.state = LoginState()
            else -> throw StacklessException("Invalid next state")
        }
    }

    private fun handleVirtualHost(handler: MinecraftHandler, packet: Handshake) {
        val virtualPort = packet.port
        val extraData = packet.address.substringAfter(0.toChar(), missingDelimiterValue = "").ifEmpty { null }
        val virtualHostNoExtra = packet.address.substringBefore(0.toChar())

        var parsed = AddressParser().parse(virtualHostNoExtra, VIAaaSConfig.hostName)
        val hadHostname = parsed.viaSuffix != null
        var usedDefault = false

        if (!hadHostname) {
            var defaultBack = VIAaaSConfig.defaultParameters[virtualPort]
            if (defaultBack == null) defaultBack = VIAaaSConfig.defaultParameters[-1]
            if (defaultBack != null) {
                parsed = defaultBack
                usedDefault = true
            }
        }

        val backProto = parsed.protocol ?: AUTO

        val backAddress = parsed.serverAddress!!
        val port = parsed.port ?: VIAaaSConfig.defaultBackendPort ?: virtualPort
        val host = HostAndPort.fromParts(backAddress, port)

        val frontOnline = parsed.online

        val addressFromWeb = VIAaaSConfig.hostName.any { parsed.serverAddress.equals(it, ignoreCase = true) }

        handler.data.backServerVer = backProto
        if (backProto == AUTO) handler.data.autoDetectProtocol = true
        (handler.data.state as? LoginState)?.also {
            it.frontOnline = frontOnline
            it.backName = parsed.username
            if (!addressFromWeb) {
                it.backAddress = host
            }
            it.extraData = extraData
        }
        (handler.data.state as? StatusState)?.also {
            if (!addressFromWeb) {
                it.address = host
            }
        }

        val playerAddr = handler.data.frontHandler.endRemoteAddress
        mcLogger.debug(
            "HS: {} {} {} {} v{}",
            playerAddr, handler.data.state.state.name, virtualHostNoExtra, virtualPort, handler.data.frontVer
        )

        if (!usedDefault && !hadHostname && VIAaaSConfig.requireHostName && !addressFromWeb
        ) {
            throw StacklessException("Missing parts in hostname")
        }
    }

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet !is Handshake) throw StacklessException("Invalid packet!")

        handleNextState(handler, packet)
        checkRateLimit(handler, packet.nextState)
        handleVirtualHost(handler, packet)

        handler.data.state.start(handler)
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close() // Not worth logging
    }
}
