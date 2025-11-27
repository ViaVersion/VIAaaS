package com.viaversion.aas.handler.state

import com.google.common.net.HostAndPort
import com.viaversion.aas.*
import com.viaversion.aas.codec.packet.handshake.Handshake
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.BackEndInit
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.autoprotocol.ProtocolDetector
import com.viaversion.aas.handler.forward
import com.viaversion.aas.util.IntendedState
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Types
import io.ktor.server.netty.*
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.handler.proxy.ProxyHandler
import io.netty.resolver.NoopAddressResolverGroup
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.URI
import kotlin.math.ceil

private suspend fun createBackChannel(
    handler: MinecraftHandler,
    socketAddr: InetSocketAddress,
    state: IntendedState,
    extraData: String?,
    proxyUri: URI?,
    proxyAddress: InetSocketAddress?
): Channel {
    autoDetectVersion(handler, socketAddr)

    val loop = handler.data.frontChannel.eventLoop()
    val channel = Bootstrap()
        .handler(BackEndInit(handler.data, proxyUri, proxyAddress))
        .channelFactory(channelSocketFactory())
        .group(loop)
        .option(ChannelOption.WRITE_BUFFER_WATER_MARK, AspirinServer.bufferWaterMark)
        .option(ChannelOption.IP_TOS, 0x18)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.TCP_FASTOPEN_CONNECT, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000) // We need to show the error before the client timeout
        .resolver(NoopAddressResolverGroup.INSTANCE)
        .connect(socketAddr)
        .also { it.suspendAwait() }
        .channel()
    (channel.pipeline()["proxy"] as? ProxyHandler)?.connectFuture()?.suspendAwait()

    if (state == IntendedState.LOGIN) {
        mcLogger.info("+ L {} -> {}", handler.endRemoteAddress, socketAddr)
    } else {
        mcLogger.debug("+ {} {} -> {}", state.name[0], handler.endRemoteAddress, socketAddr)
    }
    handler.data.backChannel = channel as SocketChannel

    val packet = Handshake()
    packet.intendedState = state
    packet.protocolId = handler.data.frontVer!!.version
    packet.address = socketAddr.hostString + if (extraData != null) 0.toChar() + extraData else ""
    packet.port = socketAddr.port

    forward(handler, packet, true)

    handler.data.frontChannel.setAutoRead(true)

    return channel
}

private suspend fun autoDetectVersion(handler: MinecraftHandler, socketAddr: InetSocketAddress) {
    if (handler.data.autoDetectProtocol) { // Auto
        var detectedProtocol: ProtocolVersion? = null
        try {
            detectedProtocol = withTimeout(10_000) {
                ProtocolDetector.detectVersion(socketAddr).await()
            }
        } catch (e: Exception) {
            mcLogger.warn("Failed to detect version of {}: {}", socketAddr, e.toString())
            mcLogger.debug("Stacktrace: ", e)
        }

        handler.data.backServerVer = if (detectedProtocol != null
            && detectedProtocol.version !in arrayOf(-1, -2)
            && ProtocolVersion.isRegistered(detectedProtocol.version)
        ) detectedProtocol else ProtocolVersion.v1_8 // fallback
    }
}

private suspend fun tryBackAddresses(
    handler: MinecraftHandler,
    addresses: Iterable<InetSocketAddress>,
    state: IntendedState,
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
                throw StacklessException("Address not allowed")
            }

            val proxyUri = VIAaaSConfig.backendProxy
            val proxySocket = if (proxyUri == null) null else {
                InetSocketAddress(AspirinServer.dnsResolver.resolve(proxyUri.host).suspendAwait(), proxyUri.port)
            }

            createBackChannel(handler, socketAddr, state, extraData, proxyUri, proxySocket)
            return // Finally it worked!
        } catch (e: Exception) {
            latestException = e
        }
    }

    throw latestException ?: StacklessException("No address found")
}

private suspend fun resolveBackendAddresses(hostAndPort: HostAndPort): List<InetSocketAddress> {
    val srvResolved = resolveSrv(hostAndPort) ?: hostAndPort

    val removedEndDot = srvResolved.host.replace(Regex("\\.$"), "")

    return when {
        removedEndDot.endsWith(".onion", ignoreCase = true) ->
            listOf(InetSocketAddress.createUnresolved(removedEndDot, srvResolved.port))

        else -> AspirinServer.dnsResolver
            .resolveAll(srvResolved.host)
            .suspendAwait()
            .groupBy { it is Inet4Address }
            .toSortedMap() // I'm sorry, IPv4, but my true love is IPv6... We can still be friends though...
            .map { InetSocketAddress(it.value.random(), srvResolved.port) }
    }
}

suspend fun connectBack(
    handler: MinecraftHandler,
    address: HostAndPort,
    state: IntendedState,
    extraData: String? = null
) {
    val addresses = resolveBackendAddresses(address)

    if (addresses.isEmpty()) throw StacklessException("Hostname has no IP addresses")

    tryBackAddresses(handler, addresses, state, extraData)
}

val openAuthMagic = 0xfdebf3fd.toInt()

// https://github.com/RaphiMC/OpenAuthMod/blob/fa66e78fe5c0e748c1b8c61624bf283fb8bc06dd/src/main/java/com/github/oam/utils/IntTo3ByteCodec.java#L16
fun encodeCompressionOpenAuth(channel: String, id: Int, data: ByteArray): IntArray {
    val buffer = ByteBufAllocator.DEFAULT.buffer()
    try {
        Types.STRING.write(buffer, channel)
        Types.VAR_INT.writePrimitive(buffer, id)
        buffer.writeBytes(data)
        val out = IntArray(2 + ceil(buffer.readableBytes() / 3.0).toInt())

        out[0] = openAuthMagic
        out[out.size - 1] = openAuthMagic

        for (i in 1 until out.size - 1) {
            var entry = 0xC0000000.toInt().or(buffer.readUnsignedByte().toInt().shl(16))
            if (buffer.isReadable) {
                entry = entry.or(0x20000000).or(buffer.readUnsignedByte().toInt().shl(8))
            }
            if (buffer.isReadable) {
                entry = entry.or(0x10000000).or(buffer.readUnsignedByte().toInt())
            }

            out[i] = entry
        }

        return out
    } finally {
        buffer.release()
    }
}