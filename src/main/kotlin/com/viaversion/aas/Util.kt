package com.viaversion.aas

import com.google.common.net.HostAndPort
import com.google.common.net.UrlEscapers
import com.google.common.primitives.Ints
import com.google.gson.JsonObject
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.util.NettyTransportTypes
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.type.Types
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.netty.*
import io.netty.buffer.ByteBuf
import io.netty.channel.*
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.dns.DefaultDnsQuestion
import io.netty.handler.codec.dns.DefaultDnsRecordDecoder
import io.netty.handler.codec.dns.DnsRawRecord
import io.netty.handler.codec.dns.DnsRecord
import io.netty.handler.codec.dns.DnsRecordType
import io.netty.util.ReferenceCountUtil
import org.slf4j.LoggerFactory
import java.lang.IllegalArgumentException
import java.math.BigInteger
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

val badLength = DecoderException("Invalid length!")
val mcLogger = LoggerFactory.getLogger("VIAaaS MC")
val webLogger = LoggerFactory.getLogger("VIAaaS Web")
val viaaasLogger = LoggerFactory.getLogger("VIAaaS")
val viaaasLoggerJava = Logger.getLogger("VIAaaS")

val secureRandom = SecureRandom()

data class SrvRecord(val priority: Int, val weight: Int, val port: Int, val target: String)


suspend fun resolveSrv(hostAndPort: HostAndPort): HostAndPort? {
    if (hostAndPort.host.endsWith(".onion", ignoreCase = true)) return null
    if (hostAndPort.port != 25565) return null

    val candidates = mutableListOf<SrvRecord>()
    try {
        // based on https://github.com/Camotoy/PacketLib/blob/312cff5f975be54cf2d92208ae2947dbda8b9f59/src/main/java/com/github/steveice10/packetlib/tcp/TcpClientSession.java
        val records: List<DnsRecord> = AspirinServer.dnsResolver
            .resolveAll(DefaultDnsQuestion("_minecraft._tcp.${hostAndPort.host}", DnsRecordType.SRV))
            .suspendAwait()
        try {
            records.forEach { record ->
                if (record.type() != DnsRecordType.SRV) return@forEach
                if (record !is DnsRawRecord) return@forEach

                candidates.add(readSrvContent(record.content()))
            }
        } finally {
            records.forEach { ReferenceCountUtil.release(it) }
        }
    } catch (ignored: Exception) {
    }

    if (candidates.isEmpty()) return null

    candidates.sortBy { it.priority }

    val highPriority = candidates[0].priority
    val selected = candidates.takeWhile { it.priority == highPriority }.randomWeighted { it.weight }

    return HostAndPort.fromParts(selected.target, selected.port)
}

fun readSrvContent(byteBuf: ByteBuf): SrvRecord {
    return SrvRecord(
        byteBuf.readUnsignedShort(),
        byteBuf.readUnsignedShort(),
        byteBuf.readUnsignedShort(),
        DefaultDnsRecordDecoder.decodeName(byteBuf)
    )
}

fun <T> List<T>.randomWeighted(weightSelector: (T) -> Int): T {
    val totalWeight = this.sumOf(weightSelector)

    if (totalWeight < 0) throw IllegalArgumentException("Weight can't be negative.")
    if (totalWeight == 0) return random()

    val value = ThreadLocalRandom.current().nextInt(totalWeight)
    var current = 0

    for (item in this) {
        current += weightSelector(item)
        if (current > value) return item
    }

    return this.first()
}

// https://medium.com/asecuritysite-when-bob-met-alice/whats-so-special-about-pkcs-1-v1-5-and-the-attack-that-just-won-t-go-away-51ccf35d65b7
fun decryptRsa(privateKey: PrivateKey, data: ByteArray) = Cipher.getInstance("RSA/ECB/PKCS1Padding").let {
    it.init(Cipher.DECRYPT_MODE, privateKey)
    it.doFinal(data)
}

fun encryptRsa(publicKey: PublicKey, data: ByteArray) = Cipher.getInstance("RSA/ECB/PKCS1Padding").let {
    it.init(Cipher.ENCRYPT_MODE, publicKey)
    it.doFinal(data)
}

fun aesKey(key: ByteArray) = SecretKeySpec(key, "AES")

@OptIn(ExperimentalUuidApi::class)
fun parseUndashedUuid(string: String): UUID {
    return Uuid.parseHex(string).toJavaUuid()
}

fun UUID.toHexString(): String {
    return this.toString().filterNot { it == '-' }
}

// https://github.com/VelocityPowered/Velocity/blob/0dd6fe1ef2783fe1f9322af06c6fd218aa67cdb1/proxy/src/main/java/com/velocitypowered/proxy/util/EncryptionUtils.java
fun generateServerHash(serverId: String, sharedSecret: ByteArray, key: PublicKey): String {
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
fun generateOfflinePlayerUuid(username: String): UUID {
    return UUID.nameUUIDFromBytes("OfflinePlayer:$username".encodeToByteArray())
}

fun isOfflinePlayer(uuid: UUID): Boolean {
    return uuid.version() == 3
}

fun checkLocalAddress(inetAddress: InetAddress): Boolean {
    return VIAaaSConfig.blockLocalAddress && (inetAddress.isAnyLocalAddress
            || inetAddress.isLinkLocalAddress
            || inetAddress.isLoopbackAddress
            || inetAddress.isSiteLocalAddress
            || inetAddress.isMCLinkLocal
            || inetAddress.isMCNodeLocal
            || inetAddress.isMCOrgLocal
            || inetAddress.isMCSiteLocal
            || ((inetAddress as? Inet6Address)?.isUniqueLocalAddress() ?: false)
            || NetworkInterface.networkInterfaces().flatMap { it.inetAddresses() }
        .anyMatch {
            // This public address acts like a localhost, let's block it
            it == inetAddress
        })
}

fun Inet6Address.isUniqueLocalAddress(): Boolean {
    val firstByte = this.address[0].toUByte().toInt()

    return (firstByte and 0xFE) == 0xFC
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

fun readRemainingBytes(byteBuf: ByteBuf) = Types.REMAINING_BYTES.read(byteBuf)!!
fun ByteBuf.readByteArray(length: Int) = ByteArray(length).also { readBytes(it) }

suspend fun hasJoined(username: String, hash: String): JsonObject {
    try {
        val req = AspirinServer.httpClient.get(
            "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                    UrlEscapers.urlFormParameterEscaper().escape(username) + "&serverId=$hash"
        )
        if (!req.status.isSuccess() || req.status == HttpStatusCode.NoContent) throw StacklessException("http code ${req.status}")
        return req.body()
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

fun sha512Hex(data: ByteArray): String {
    return MessageDigest.getInstance("SHA-512").digest(data).toHexString()
}

fun eventLoopGroup(): EventLoopGroup {
    return MultiThreadIoEventLoopGroup(NettyTransportTypes.getDefault().ioHandlerFactory)
}

fun channelServerSocketFactory(): ChannelFactory<ServerSocketChannel> {
    return NettyTransportTypes.getDefault().serverChannelFactory
}

fun channelSocketFactory(): ChannelFactory<SocketChannel> {
    return NettyTransportTypes.getDefault().channelFactory
}

fun channelDatagramFactory(): ChannelFactory<DatagramChannel> {
    return NettyTransportTypes.getDefault().datagramChannelFactory
}

fun reverseLookup(address: InetAddress): String {
    val bytes = address.address
    return if (bytes.size == 4) {
        // IPv4
        bytes.reversed()
            .joinToString(".") { it.toUByte().toString() } + ".in-addr.arpa"
    } else { // IPv6
        bytes.toHexString()
            .reversed().toCharArray()
            .joinToString(".") + ".ip6.arpa"
    }
}

fun Channel.fireExceptionCaughtIfOpen(e: Exception) {
    if (isOpen) pipeline().fireExceptionCaught(e)
}