package com.viaversion.aas.handler

import com.viaversion.aas.codec.CompressionCodec
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.readRemainingBytes
import com.viaversion.aas.send
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelPipeline
import io.netty.handler.proxy.HttpProxyHandler
import io.netty.handler.proxy.Socks4ProxyHandler
import io.netty.handler.proxy.Socks5ProxyHandler
import java.net.InetSocketAddress
import java.net.URI

fun forward(handler: MinecraftHandler, packet: Packet, flush: Boolean = false) {
    send(handler.other!!, packet, flush)
}

fun is17(handler: MinecraftHandler) = handler.data.frontVer!! <= ProtocolVersion.v1_7_6.version

fun addProxyHandler(pipe: ChannelPipeline, proxyUri: URI?, socket: InetSocketAddress?) {
    if (proxyUri != null) {
        val user = proxyUri.userInfo?.substringBefore(':')
        val pass = proxyUri.userInfo?.substringAfter(':')
        val handler = when (proxyUri.scheme) {
            "socks5" -> Socks5ProxyHandler(socket, user, pass)
            "socks4" -> Socks4ProxyHandler(socket, user)
            "http" -> if (user != null) HttpProxyHandler(socket, user, pass) else HttpProxyHandler(socket)
            else -> null
        }
        if (handler != null) pipe.addFirst("proxy", handler)
    }
}

fun decodeBrand(data: ByteArray, is17: Boolean): String {
    return if (is17) {
        String(data, Charsets.UTF_8)
    } else {
        Type.STRING.read(Unpooled.wrappedBuffer(data))
    }
}

fun encodeBrand(string: String, is17: Boolean): ByteArray {
    return if (is17) {
        string.toByteArray(Charsets.UTF_8)
    } else {
        val buf = ByteBufAllocator.DEFAULT.buffer()
        try {
            Type.STRING.write(buf, string)
            readRemainingBytes(buf)
        } finally {
            buf.release()
        }
    }
}


fun setCompression(channel: Channel, threshold: Int) {
    val pipe = channel.pipeline()

    if (threshold == -1) {
        if (pipe["compress"] != null) pipe.remove("compress")
    } else {
        if (pipe["compress"] != null) {
            pipe[CompressionCodec::class.java].setThreshold(threshold)
        } else {
            pipe.addAfter("frame", "compress", CompressionCodec(threshold))
        }
    }
}