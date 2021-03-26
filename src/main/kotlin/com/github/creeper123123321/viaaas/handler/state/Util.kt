package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.*
import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.handler.BackEndInit
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import com.github.creeper123123321.viaaas.packet.handshake.Handshake
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import us.myles.ViaVersion.packets.State
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

private fun createBackChannel(handler: MinecraftHandler, socketAddr: InetSocketAddress, state: State): ChannelFuture {
    return Bootstrap()
        .handler(BackEndInit(handler.data))
        .channelFactory(channelSocketFactory())
        .group(handler.data.frontChannel.eventLoop())
        .option(ChannelOption.IP_TOS, 0x18)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000) // We need to show the error before the client timeout
        .connect(socketAddr)
        .addListener(ChannelFutureListener {
            if (it.isSuccess) {
                mcLogger.info("+ ${handler.endRemoteAddress} -> $socketAddr")
                handler.data.backChannel = it.channel() as SocketChannel

                val packet = Handshake()
                packet.nextState = state
                packet.protocolId = handler.data.frontVer!!
                packet.address = socketAddr.hostString
                packet.port = socketAddr.port

                forward(handler, packet, true)

                handler.data.frontChannel.setAutoRead(true)
            }
        })
}

private fun tryBackAddress(
    handler: MinecraftHandler,
    iterator: Iterator<InetAddress>,
    port: Int,
    state: State,
    success: () -> Unit,
) {
    val fail = { e: Throwable ->
        if (!iterator.hasNext()) {
            // We're in the event loop
            handler.disconnect("Couldn't connect: $e")
        } else if (handler.data.frontChannel.isActive) {
            tryBackAddress(handler, iterator, port, state, success)
        }
    }
    try {
        val socketAddr = InetSocketAddress(iterator.next(), port)

        if (checkLocalAddress(socketAddr.address)
            || matchesAddress(socketAddr, VIAaaSConfig.blockedBackAddresses)
            || !matchesAddress(socketAddr, VIAaaSConfig.allowedBackAddresses)
        ) {
            throw SecurityException("Not allowed")
        }

        createBackChannel(handler, socketAddr, state).addListener {
            if (it.isSuccess) {
                success()
            } else {
                fail(it.cause())
            }
        }
    } catch (e: Exception) {
        handler.data.frontChannel.eventLoop().submit {
            fail(e)
        }
    }
}

fun connectBack(handler: MinecraftHandler, address: String, port: Int, state: State, success: () -> Unit) {
    handler.data.frontChannel.setAutoRead(false)
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val srvResolved = resolveSrv(address, port)

            val iterator = InetAddress.getAllByName(srvResolved.first)
                .groupBy { it is Inet4Address }
                .toSortedMap() // I'm sorry, IPv4, but my true love is IPv6... We can still be friends though...
                .map { it.value.random() }
                .iterator()

            if (!iterator.hasNext()) throw IllegalArgumentException("Hostname has no IP address")
            tryBackAddress(handler, iterator, srvResolved.second, state, success)
        } catch (e: Exception) {
            handler.data.frontChannel.eventLoop().submit {
                handler.disconnect("Couldn't connect: $e")
            }
        }
    }
}
