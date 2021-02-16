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
import java.net.InetAddress
import java.net.InetSocketAddress

fun createBackChannel(handler: MinecraftHandler, socketAddr: InetSocketAddress, state: State): ChannelFuture {
    return Bootstrap()
        .handler(BackEndInit(handler.data))
        .channelFactory(channelSocketFactory())
        .group(handler.data.frontChannel.eventLoop())
        .option(ChannelOption.IP_TOS, 0x18)
        .option(ChannelOption.TCP_NODELAY, true)
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000) // Half of mc timeout
        .connect(socketAddr)
        .addListener(ChannelFutureListener {
            if (it.isSuccess) {
                mcLogger.info("Connected ${handler.remoteAddress} -> $socketAddr")
                handler.data.backChannel = it.channel() as SocketChannel

                val packet = Handshake()
                packet.nextState = state
                packet.protocolId = handler.data.frontVer!!
                packet.address = socketAddr.hostString
                packet.port = socketAddr.port

                forward(handler, packet, true)

                handler.data.frontChannel.setAutoRead(true)
            } else {
                // We're in the event loop
                handler.disconnect("Couldn't connect: " + it.cause().toString())
            }
        })
}

fun connectBack(handler: MinecraftHandler, address: String, port: Int, state: State, success: () -> Unit) {
    handler.data.frontChannel.setAutoRead(false)
    GlobalScope.launch(Dispatchers.IO) {
        try {
            val srvResolved = resolveSrv(address, port)

            val socketAddr = InetSocketAddress(InetAddress.getByName(srvResolved.first), srvResolved.second)

            if (checkLocalAddress(socketAddr.address)
                || matchesAddress(socketAddr, VIAaaSConfig.blockedBackAddresses)
                || !matchesAddress(socketAddr, VIAaaSConfig.allowedBackAddresses)
            ) {
                throw SecurityException("Not allowed")
            }

            createBackChannel(handler, socketAddr, state).addListener { if (it.isSuccess) success() }
        } catch (e: Exception) {
            handler.data.frontChannel.eventLoop().submit {
                handler.disconnect("Couldn't connect: $e")
            }
        }
    }
}