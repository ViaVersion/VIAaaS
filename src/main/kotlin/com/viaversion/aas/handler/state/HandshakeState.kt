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
import com.viaversion.aas.util.IntendedState
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

    private fun checkRateLimit(handler: MinecraftHandler, state: IntendedState) {
        val socketAddress = (handler.endRemoteAddress as InetSocketAddress).address
        val limit = RateLimit.rateLimitByIp[socketAddress]

        if (!limit.handshakeLimiter.tryAcquire()
            || (state == IntendedState.LOGIN && !limit.loginLimiter.tryAcquire())
        ) {
            throw StacklessException("Rate-limited")
        }
    }

    private fun handleNextState(handler: MinecraftHandler, packet: Handshake) {
        handler.data.frontVer = ProtocolVersion.getProtocol(packet.protocolId)

        when (packet.intendedState) {
            IntendedState.STATUS -> handler.data.state = StatusState()
            IntendedState.LOGIN -> handler.data.state = LoginState()
            else -> throw StacklessException("Invalid next state")
        }
    }

    private fun handleVirtualHost(handler: MinecraftHandler, packet: Handshake) {
        val virtualPort = packet.port
        val extraData = packet.address.let(::extractExtraHandshakeData)
        val virtualHostNoExtra = packet.address.let(::removeHandshakeExtraData)

        val useWebParameters = matchesBaseHostname(virtualHostNoExtra)

        var parsed: AddressParser? = null
        var parsedHadViaSuffix = false
        if (!useWebParameters) {
            parsed = AddressParser().parse(virtualHostNoExtra, VIAaaSConfig.hostName)
            parsedHadViaSuffix = parsed.viaSuffix != null
            if (!parsedHadViaSuffix) parsed = null
        }

        val backProto = parsed?.protocol ?: AUTO
        val backAutoDetect = backProto == AUTO
        val backendHostPort = parsed?.let(::hostPortFromParser)

        handler.data.backServerVer = backProto
        handler.data.autoDetectProtocol = backAutoDetect
        (handler.data.state as? LoginState)?.also {
            it.frontOnline = parsed?.online
            it.backName = parsed?.username
            it.backAddress = backendHostPort
            it.extraData = extraData
        }
        (handler.data.state as? StatusState)?.also {
            it.address = backendHostPort
        }

        val playerAddr = handler.data.frontHandler.endRemoteAddress
        mcLogger.debug(
            "HS: {} {} {} {} v{}",
            playerAddr, handler.data.state.state.name, virtualHostNoExtra, virtualPort, handler.data.frontVer
        )

        if (!parsedHadViaSuffix && !useWebParameters) {
            throw StacklessException("Missing parts in hostname")
        }
    }

    private fun extractExtraHandshakeData(hsData: String): String? {
        return hsData.substringAfter(0.toChar(), missingDelimiterValue = "").ifEmpty { null }
    }

    private fun removeHandshakeExtraData(hsData: String) = hsData.substringBefore(0.toChar())

    private fun matchesBaseHostname(hostAddress: String) : Boolean {
        val cleanedAddress = hostAddress.removeSuffix(".")
        return VIAaaSConfig.hostName.any { cleanedAddress.equals(it, ignoreCase = true) }
    }

    private fun hostPortFromParser(parsed: AddressParser): HostAndPort {
        return HostAndPort.fromParts(parsed.serverAddress, parsed.port ?: 25565)
    }

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet !is Handshake) throw StacklessException("Invalid packet!")

        handleNextState(handler, packet)
        checkRateLimit(handler, packet.intendedState)
        handleVirtualHost(handler, packet)
        checkVersionAccepted(packet.intendedState, packet.protocolId)

        handler.data.state.start(handler)
    }

    private fun checkVersionAccepted(intendedState: IntendedState, protocolId: Int) {
        if (intendedState == IntendedState.LOGIN && !ProtocolVersion.isRegistered(protocolId)) {
            throw StacklessException("Your client version ($protocolId) is unsupported")
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close() // Not worth logging
    }
}
