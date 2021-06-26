package com.viaversion.aas.handler.state

import com.google.common.net.HostAndPort
import com.viaversion.aas.*
import com.viaversion.aas.codec.packet.handshake.Handshake
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.BackEndInit
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.autoprotocol.ProtocolDetector
import com.viaversion.aas.handler.forward
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.ktor.server.netty.*
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.resolver.NoopAddressResolverGroup
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetSocketAddress

private suspend fun createBackChannel(
    handler: MinecraftHandler,
    socketAddr: InetSocketAddress,
    state: State,
    extraData: String?
): Channel {
    val loop = handler.data.frontChannel.eventLoop()
    val channel = Bootstrap()
        .handler(BackEndInit(handler.data))
        .channelFactory(channelSocketFactory(loop.parent()))
        .group(loop)
        .option(ChannelOption.WRITE_BUFFER_WATER_MARK, bufferWaterMark)
        .option(ChannelOption.IP_TOS, 0x18)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000) // We need to show the error before the client timeout
        .resolver(NoopAddressResolverGroup.INSTANCE)
        .connect(socketAddr)
        .also { it.suspendAwait() }
        .channel()

    mcLogger.info("+ ${handler.endRemoteAddress} -> $socketAddr")
    handler.data.backChannel = channel as SocketChannel

    autoDetectVersion(handler, socketAddr)

    val packet = Handshake()
    packet.nextState = state
    packet.protocolId = handler.data.frontVer!!
    packet.address = socketAddr.hostString + if (extraData != null) 0.toChar() + extraData else ""
    packet.port = socketAddr.port

    forward(handler, packet, true)

    handler.data.frontChannel.setAutoRead(true)

    return channel
}

private suspend fun autoDetectVersion(handler: MinecraftHandler, socketAddr: InetSocketAddress) {
    if (handler.data.backServerVer == -2) { // Auto
        try {
            val detectedProtocol = withTimeoutOrNull(10_000) {
                ProtocolDetector.detectVersion(socketAddr).await()
            }

            if (detectedProtocol != null
                && detectedProtocol.version != -1
                && ProtocolVersion.isRegistered(detectedProtocol.version)) {
                handler.data.backServerVer = detectedProtocol.version
            } else {
                handler.data.backServerVer = 47 // fallback 1.8
            }
        } catch (e: Exception) {
            mcLogger.warn("Failed to auto-detect version for $socketAddr: $e")
            mcLogger.debug("Stacktrace: ", e)
        }
    }
}

private suspend fun tryBackAddresses(
    handler: MinecraftHandler,
    addresses: Iterable<InetSocketAddress>,
    state: State,
    extraData: String?
) {
    var latestException: Exception? = null
    for (socketAddr in addresses) {
        try {
            if (!handler.data.frontChannel.isActive) return
            if ((socketAddr.address != null && checkLocalAddress(socketAddr.address))
                || matchesAddress(socketAddr, VIAaaSConfig.blockedBackAddresses)
                || !matchesAddress(socketAddr, VIAaaSConfig.allowedBackAddresses)
            ) {
                throw StacklessException("Not allowed")
            }

            createBackChannel(handler, socketAddr, state, extraData)
            return // Finally it worked!
        } catch (e: Exception) {
            latestException = e
        }
    }

    throw latestException ?: StacklessException("No address found")
}

private suspend fun resolveBackendAddresses(hostAndPort: HostAndPort): List<InetSocketAddress> {
    val srvResolved = resolveSrv(hostAndPort)

    val removedEndDot = srvResolved.host.replace(Regex("\\.$"), "")

    return when {
        removedEndDot.endsWith(".onion", ignoreCase = true) ->
            listOf(InetSocketAddress.createUnresolved(removedEndDot, srvResolved.port))
        else -> dnsResolver
            .resolveAll(srvResolved.host)
            .suspendAwait()
            .groupBy { it is Inet4Address }
            .toSortedMap() // I'm sorry, IPv4, but my true love is IPv6... We can still be friends though...
            .map { InetSocketAddress(it.value.random(), srvResolved.port) }
    }
}

suspend fun connectBack(
    handler: MinecraftHandler,
    address: String,
    port: Int,
    state: State,
    extraData: String? = null
) {
    try {
        val addresses = resolveBackendAddresses(HostAndPort.fromParts(address, port))

        if (addresses.isEmpty()) throw StacklessException("Hostname has no IP addresses")

        tryBackAddresses(handler, addresses, state, extraData)
    } catch (e: Exception) {
        throw StacklessException("Connect: $e", e)
    }
}
