package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.github.creeper123123321.viaaas.packet.Packet
import com.github.creeper123123321.viaaas.send
import io.netty.channel.ChannelPipeline
import io.netty.handler.proxy.Socks5ProxyHandler
import us.myles.ViaVersion.api.protocol.ProtocolVersion
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