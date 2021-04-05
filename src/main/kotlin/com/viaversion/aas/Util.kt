package com.viaversion.aas

import com.viaversion.aas.config.VIAaaSConfig
import com.google.common.base.Preconditions
import com.google.common.net.UrlEscapers
import com.google.common.primitives.Ints
import com.google.gson.JsonObject
import com.viaversion.aas.util.StacklessException
import io.ktor.client.request.*
import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.DecoderException
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.naming.directory.InitialDirContext

val badLength = DecoderException("Invalid length!")
val mcLogger = LoggerFactory.getLogger("VIAaaS MC")
val webLogger = LoggerFactory.getLogger("VIAaaS Web")
val viaaasLogger = LoggerFactory.getLogger("VIAaaS")

val secureRandom = if (VIAaaSConfig.useStrongRandom) SecureRandom.getInstanceStrong() else SecureRandom()

fun resolveSrv(address: String, port: Int): Pair<String, Int> {
    if (port == 25565) {
        try {
            // https://github.com/GeyserMC/Geyser/blob/99e72f35b308542cf0dbfb5b58816503c3d6a129/connector/src/main/java/org/geysermc/connector/GeyserConnector.java
            val attr = InitialDirContext()
                .getAttributes("dns:///_minecraft._tcp.$address", arrayOf("SRV"))["SRV"]
            if (attr != null && attr.size() > 0) {
                val record = (attr.get(0) as String).split(" ")
                return record[3] to record[2].toInt()
            }
        } catch (ignored: Exception) { // DuckDNS workaround
        }
    }
    return address to port
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
    return (matchAddress(addr.hostString, list) || (addr.address != null
            && (matchAddress(addr.address.hostAddress, list) || matchAddress(addr.address.hostName, list))))
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

fun writeFlushClose(ch: Channel, obj: Any) {
    ch.writeAndFlush(obj).addListener(ChannelFutureListener.CLOSE)
}

fun readRemainingBytes(byteBuf: ByteBuf) = Type.REMAINING_BYTES.read(byteBuf)
fun ByteBuf.readByteArray(length: Int) = ByteArray(length).also { readBytes(it) }

suspend fun hasJoined(username: String, hash: String): JsonObject {
    return httpClient.get(
        "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                UrlEscapers.urlFormParameterEscaper().escape(username) +
                "&serverId=$hash"
    ) ?: throw StacklessException("Couldn't authenticate with session servers")
}

fun generate128Bits() = ByteArray(16).also { secureRandom.nextBytes(it) }
fun generateServerId() = ByteArray(13).let {
    secureRandom.nextBytes(it)
    Base64.getEncoder().withoutPadding().encodeToString(it)
    // https://developer.mozilla.org/en-US/docs/Glossary/Base64 133% of original
}

fun Int.parseProtocol() = ProtocolVersion.getProtocol(this)
