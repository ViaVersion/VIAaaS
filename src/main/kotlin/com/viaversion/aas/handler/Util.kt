package com.viaversion.aas.handler

import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.packet.Packet
import com.viaversion.aas.send
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import io.netty.channel.ChannelPipeline
import io.netty.handler.proxy.Socks5ProxyHandler
import java.net.InetSocketAddress

fun forward(handler: MinecraftHandler, packet: Packet, flush: Boolean = false) {
    send(handler.other!!, packet, flush)
}

fun is1_7(handler: MinecraftHandler) = handler.data.frontVer!! <= ProtocolVersion.v1_7_6.version

fun addSocks5(pipe: ChannelPipeline) {
    val addr = VIAaaSConfig.backendSocks5ProxyAddress
    if (addr != null) {
        pipe.addFirst(Socks5ProxyHandler(InetSocketAddress(addr, VIAaaSConfig.backendSocks5ProxyPort)))
    }
}