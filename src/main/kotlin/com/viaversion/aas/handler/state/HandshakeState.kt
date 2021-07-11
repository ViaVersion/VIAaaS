package com.viaversion.aas.handler.state

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.net.HostAndPort
import com.google.common.util.concurrent.RateLimiter
import com.viaversion.aas.VIAaaSAddress
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.handshake.Handshake
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.mcLogger
import com.viaversion.aas.setAutoRead
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        val limit = RateLimit.rateLimitByIp.get(socketAddress)

        if (!limit.handshakeLimiter.tryAcquire()
            || (state == State.LOGIN && !limit.loginLimiter.tryAcquire())
        ) {
            throw StacklessException("Rate-limited")
        }
    }

    private fun handleNextState(handler: MinecraftHandler, packet: Handshake) {
        handler.data.frontVer = packet.protocolId
        when (packet.nextState.ordinal) {
            1 -> handler.data.state = StatusState
            2 -> handler.data.state = LoginState()
            else -> throw StacklessException("Invalid next state")
        }
    }

    private fun handleVirtualHost(handler: MinecraftHandler, packet: Handshake) {
        val virtualPort = packet.port
        val extraData = packet.address.substringAfter(0.toChar(), missingDelimiterValue = "").ifEmpty { null }
        val virtualHostNoExtra = packet.address.substringBefore(0.toChar())

        val parsed = VIAaaSConfig.hostName.map {
            VIAaaSAddress().parse(virtualHostNoExtra, it)
        }.sortedBy {
            it.viaSuffix == null
        }.first()

        val backProto = parsed.protocol ?: -2
        val hadHostname = parsed.viaSuffix != null

        packet.address = parsed.serverAddress!!
        packet.port = parsed.port ?: VIAaaSConfig.defaultBackendPort ?: virtualPort

        var frontOnline = parsed.online
        if (VIAaaSConfig.forceOnlineMode) frontOnline = true

        handler.data.backServerVer = backProto
        (handler.data.state as? LoginState)?.also {
            it.frontOnline = frontOnline
            it.backName = parsed.username
            it.backAddress = HostAndPort.fromParts(packet.address, packet.port)
            it.extraData = extraData
        }

        val playerAddr = handler.data.frontHandler.endRemoteAddress
        mcLogger.info(
            "HS: $playerAddr ${handler.data.state.state.toString().substring(0, 1)} " +
                    "$virtualHostNoExtra $virtualPort v${handler.data.frontVer}"
        )

        if (!hadHostname && VIAaaSConfig.requireHostName) {
            throw StacklessException("Missing parts in hostname")
        }
    }

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet !is Handshake) throw StacklessException("Invalid packet!")

        handleNextState(handler, packet)
        checkRateLimit(handler, packet.nextState)
        handleVirtualHost(handler, packet)

        connectStatus(handler, ctx, packet)
    }

    private fun connectStatus(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Handshake) {
        if (packet.nextState == State.STATUS) { // see LoginState for LOGIN
            handler.data.frontChannel.setAutoRead(false)
            handler.coroutineScope.launch(Dispatchers.IO) {
                try {
                    connectBack(handler, packet.address, packet.port, packet.nextState)
                } catch (e: Exception) {
                    ctx.fireExceptionCaught(e)
                }
            }
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close() // Not worth logging
    }

    override fun onInactivated(handler: MinecraftHandler) {
        // Not worth logging
    }
}
