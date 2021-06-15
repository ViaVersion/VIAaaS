package com.viaversion.aas

import com.google.common.base.Preconditions
import com.google.common.net.HostAndPort
import com.google.common.net.UrlEscapers
import com.google.common.primitives.Ints
import com.google.gson.JsonObject
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.ktor.client.request.*
import io.ktor.server.netty.*
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFactory
import io.netty.channel.ChannelFutureListener
import io.netty.channel.EventLoopGroup
import io.netty.channel.epoll.*
import io.netty.channel.kqueue.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.dns.DefaultDnsQuestion
import io.netty.handler.codec.dns.DefaultDnsRawRecord
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder
import io.netty.handler.codec.dns.DnsRecordType
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

val badLength = DecoderException("Invalid length!")
val mcLogger = LoggerFactory.getLogger("VIAaaS MC")
val webLogger = LoggerFactory.getLogger("VIAaaS Web")
val viaaasLogger = LoggerFactory.getLogger("VIAaaS")

val secureRandom = if (VIAaaSConfig.useStrongRandom) SecureRandom.getInstanceStrong() else SecureRandom()

suspend fun resolveSrv(hostAndPort: HostAndPort): HostAndPort {
    if (hostAndPort.host.endsWith(".onion", ignoreCase = true)) return hostAndPort
    if (hostAndPort.port == 25565) {
        try {
            // stolen from PacketLib (MIT) https://github.com/Camotoy/PacketLib/blob/312cff5f975be54cf2d92208ae2947dbda8b9f59/src/main/java/com/github/steveice10/packetlib/tcp/TcpClientSession.java
            val records = dnsResolver
                .resolveAll(DefaultDnsQuestion("_minecraft._tcp.${hostAndPort.host}", DnsRecordType.SRV))
                .suspendAwait()
            try {
                records.forEach { record ->
                    if (record is DefaultDnsRawRecord && record.type() == DnsRecordType.SRV) {
                        val content = record.content()

                        content.skipBytes(4)
                        val port = content.readUnsignedShort()
                        val address = DefaultDnsRecordDecoder.decodeName(content)

                        return HostAndPort.fromParts(address, port)
                    }
                }
            } finally {
                records.forEach { ReferenceCountUtil.release(it) }
            }
        } catch (e: Exception) {
            viaaasLogger.debug("Couldn't resolve SRV", e)
        }
    }
    return hostAndPort
}

fun decryptRsa(privateKey: PrivateKey, data: ByteArray) = Cipher.getInstance("RSA").let {
    it.init(Cipher.DECRYPT_MODE, privateKey)
    it.doFinal(data)
}

fun encryptRsa(publicKey: PublicKey, data: ByteArray) = Cipher.getInstance("RSA").let {
    it.init(Cipher.ENCRYPT_MODE, publicKey)
    it.doFinal(data)
}

fun mcCfb8(key: ByteArray, mode: Int): Cipher {
    val spec = SecretKeySpec(key, "AES")
    val iv = IvParameterSpec(key)
    return Cipher.getInstance("AES/CFB8/NoPadding").let {
        it.init(mode, spec, iv)
        it
    }
}

// https://github.com/VelocityPowered/Velocity/blob/6467335f74a7d1617512a55cc9acef5e109b51ac/api/src/main/java/com/velocitypowered/api/util/UuidUtils.java
@OptIn(ExperimentalUnsignedTypes::class)
fun parseUndashedId(string: String): UUID {
    Preconditions.checkArgument(string.length == 32, "Length is incorrect")
    return UUID(
        string.substring(0, 16).toULong(16).toLong(),
        string.substring(16).toULong(16).toLong()
    )
}

// https://github.com/VelocityPowered/Velocity/blob/0dd6fe1ef2783fe1f9322af06c6fd218aa67cdb1/proxy/src/main/java/com/velocitypowered/proxy/util/EncryptionUtils.java
fun generateServerHash(serverId: String, sharedSecret: ByteArray?, key: PublicKey): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(serverId.toByteArray(Charsets.ISO_8859_1))
    digest.update(sharedSecret)
    digest.update(key.encoded)
    return twosComplementHexdigest(digest.digest())
}

fun twosComplementHexdigest(digest: ByteArray): String {
    return BigInteger(digest).toString(16)
}

// https://github.com/VelocityPowered/Velocity/blob/e3f17eeb245b8d570f16c1f2aff5e7eafb698d5e/api/src/main/java/com/velocitypowered/api/util/UuidUtils.java
fun generateOfflinePlayerUuid(username: String) = UUID.nameUUIDFromBytes(
    "OfflinePlayer:$username".toByteArray(Charsets.UTF_8)
)

fun checkLocalAddress(inetAddress: InetAddress): Boolean {
    return VIAaaSConfig.blockLocalAddress && (inetAddress.isAnyLocalAddress
            || inetAddress.isLinkLocalAddress
            || inetAddress.isLoopbackAddress
            || inetAddress.isSiteLocalAddress
            || inetAddress.isMCLinkLocal
            || inetAddress.isMCNodeLocal
            || inetAddress.isMCOrgLocal
            || inetAddress.isMCSiteLocal
            || NetworkInterface.networkInterfaces().flatMap { it.inetAddresses() }
        .anyMatch {
            // This public address acts like a localhost, let's block it
            it == inetAddress
        })
}

fun matchesAddress(addr: InetSocketAddress, list: List<String>): Boolean {
    return matchAddress(addr.hostString, list) || (addr.address != null && matchAddress(addr.address.hostAddress, list))
}

private fun matchAddress(addr: String, list: List<String>): Boolean {
    if (list.contains("*")) return true
    val parts = addr.split(".").filter(String::isNotEmpty)
    val isNumericIp = parts.size == 4 && parts.all { Ints.tryParse(it) != null }
    return (0..parts.size).any { i: Int ->
        val query: String = if (isNumericIp) {
            parts.filterIndexed { it, _ -> it <= i }.joinToString(".") +
                    if (i != 3) ".*" else ""
        } else {
            (if (i != 0) "*." else "") +
                    parts.filterIndexed { it, _ -> it >= i }.joinToString(".")
        }
        list.contains(query)
    }
}

fun Channel.setAutoRead(b: Boolean) {
    this.config().isAutoRead = b
    if (b) this.read()
}

fun send(ch: Channel, obj: Any, flush: Boolean = false) {
    if (flush) {
        ch.writeAndFlush(obj, ch.voidPromise())
    } else {
        ch.write(obj, ch.voidPromise())
    }
}

fun writeFlushClose(ch: Channel, obj: Any, delay: Boolean = false) {
    // https://github.com/VelocityPowered/Velocity/blob/dev/1.1.0/proxy/src/main/java/com/velocitypowered/proxy/connection/MinecraftConnection.java#L252
    ch.setAutoRead(false)
    val action = {
        ch.writeAndFlush(obj).addListener(ChannelFutureListener.CLOSE)
    }
    if (delay) {
        ch.eventLoop().schedule(action, 250, TimeUnit.MILLISECONDS)
    } else {
        action()
    }
}

fun readRemainingBytes(byteBuf: ByteBuf) = Type.REMAINING_BYTES.read(byteBuf)!!
fun ByteBuf.readByteArray(length: Int) = ByteArray(length).also { readBytes(it) }

suspend fun hasJoined(username: String, hash: String): JsonObject {
    return try {
        httpClient.get(
            "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                    UrlEscapers.urlFormParameterEscaper().escape(username) + "&serverId=$hash"
        )
    } catch (e: Exception) {
        throw StacklessException("Couldn't authenticate with session servers", e)
    }
}

fun generate128Bits() = ByteArray(16).also { secureRandom.nextBytes(it) }
fun generateServerId() = ByteArray(13).let {
    secureRandom.nextBytes(it)
    Base64.getEncoder().withoutPadding().encodeToString(it)!!
    // https://developer.mozilla.org/en-US/docs/Glossary/Base64 133% of original
}

fun Int.parseProtocol() = ProtocolVersion.getProtocol(this)

fun sha512Hex(data: ByteArray): String {
    return MessageDigest.getInstance("SHA-512").digest(data)
        .asUByteArray()
        .joinToString("") { it.toString(16).padStart(2, '0') }
}

fun eventLoopGroup(): EventLoopGroup {
    if (VIAaaSConfig.isNativeTransportMc) {
        if (Epoll.isAvailable()) return EpollEventLoopGroup()
        if (KQueue.isAvailable()) return KQueueEventLoopGroup()
    }
    return NioEventLoopGroup()
}

fun channelServerSocketFactory(eventLoop: EventLoopGroup): ChannelFactory<ServerSocketChannel> {
    return when (eventLoop) {
        is EpollEventLoopGroup -> ChannelFactory { EpollServerSocketChannel() }
        is KQueueEventLoopGroup -> ChannelFactory { KQueueServerSocketChannel() }
        else -> ChannelFactory { NioServerSocketChannel() }
    }
}

fun channelSocketFactory(eventLoop: EventLoopGroup): ChannelFactory<SocketChannel> {
    return when (eventLoop) {
        is EpollEventLoopGroup -> ChannelFactory { EpollSocketChannel() }
        is KQueueEventLoopGroup -> ChannelFactory { KQueueSocketChannel() }
        else -> ChannelFactory { NioSocketChannel() }
    }
}

fun channelDatagramFactory(eventLoop: EventLoopGroup): ChannelFactory<DatagramChannel> {
    return when (eventLoop) {
        is EpollEventLoopGroup -> ChannelFactory { EpollDatagramChannel() }
        is KQueueEventLoopGroup -> ChannelFactory { KQueueDatagramChannel() }
        else -> ChannelFactory { NioDatagramChannel() }
    }
}

fun reverseLookup(address: InetAddress): String {
    val bytes = address.address
    return if (bytes.size == 4) {
        // IPv4
        bytes.reversed()
            .joinToString(".") { it.toUByte().toString() } + ".in-addr.arpa"
    } else { // IPv6
        bytes.flatMap { it.toUByte().toString(16).padStart(2, '0').toCharArray().map { it.toString() } }
            .asReversed()
            .joinToString(".") + ".ip6.arpa"
    }
}
