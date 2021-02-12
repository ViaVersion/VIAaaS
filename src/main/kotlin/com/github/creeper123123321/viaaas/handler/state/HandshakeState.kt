package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.*
import com.github.creeper123123321.viaaas.packet.handshake.Handshake
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.handler.BackEndInit
import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import io.netty.bootstrap.Bootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import us.myles.ViaVersion.packets.State
import java.net.InetAddress
import java.net.InetSocketAddress

class HandshakeState : MinecraftConnectionState {
    fun connectBack(handler: MinecraftHandler, socketAddr: InetSocketAddress): ChannelFuture {
        return Bootstrap()
            .handler(BackEndInit(handler.data))
            .channelFactory(channelSocketFactory())
            .group(handler.data.frontChannel.eventLoop())
            .option(ChannelOption.IP_TOS, 0x18)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000) // Half of mc timeout
            .connect(socketAddr)
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

        val parsed = VIAaaSAddress().parse(packet.address.substringBefore(0.toChar()), VIAaaSConfig.hostName)
        val backProto = parsed.protocol ?: 47 // todo autodetection
        val hadHostname = parsed.viaSuffix != null

        packet.address = parsed.serverAddress!!
        packet.port = parsed.port ?: if (VIAaaSConfig.defaultBackendPort == -1) {
            packet.port
        } else {
            VIAaaSConfig.defaultBackendPort
        }

        handler.data.backVer = backProto
        handler.data.frontOnline = parsed.online
        if (VIAaaSConfig.forceOnlineMode) handler.data.frontOnline = true
        handler.data.backName = parsed.username

        val playerAddr = handler.data.frontHandler.remoteAddress
        mcLogger.info("Connecting $playerAddr (${handler.data.frontVer}) -> ${packet.address}:${packet.port} ($backProto)")

        if (!hadHostname && VIAaaSConfig.requireHostName) {
            throw UnsupportedOperationException("This VIAaaS instance requires you to use the hostname")
        }

        handler.data.frontChannel.setAutoRead(false)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val srvResolved = resolveSrv(packet.address, packet.port)
                packet.address = srvResolved.first
                packet.port = srvResolved.second

                val socketAddr = InetSocketAddress(InetAddress.getByName(packet.address), packet.port)

                if (checkLocalAddress(socketAddr.address)
                    || matchesAddress(socketAddr, VIAaaSConfig.blockedBackAddresses)
                    || !matchesAddress(socketAddr, VIAaaSConfig.allowedBackAddresses)
                ) {
                    throw SecurityException("Not allowed")
                }

                val future = connectBack(handler, socketAddr)

                future.addListener {
                    if (it.isSuccess) {
                        mcLogger.info("Connected ${handler.remoteAddress} -> $socketAddr")

                        handler.data.backChannel = future.channel() as SocketChannel

                        forward(handler, packet, true)

                        handler.data.frontChannel.setAutoRead(true)
                    } else {
                        // We're in the event loop
                        handler.disconnect("Couldn't connect: " + it.cause().toString())
                    }
                }
            } catch (e: Exception) {
                handler.data.frontChannel.eventLoop().submit {
                    handler.disconnect("Couldn't connect: $e")
                }
            }
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        handler.data.frontChannel.close() // Not worth logging
    }

    override fun onInactivated(handler: MinecraftHandler) {
        // Not worth logging
    }
}