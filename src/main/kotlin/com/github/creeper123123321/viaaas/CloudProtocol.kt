package com.github.creeper123123321.viaaas

import com.google.common.net.UrlEscapers
import com.google.gson.JsonObject
import io.ktor.client.request.*
import io.ktor.http.cio.websocket.*
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import kotlinx.coroutines.runBlocking
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.Protocol
import us.myles.ViaVersion.api.protocol.ProtocolPipeline
import us.myles.ViaVersion.api.protocol.ProtocolRegistry
import us.myles.ViaVersion.api.protocol.SimpleProtocol
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.naming.NameNotFoundException
import javax.naming.directory.InitialDirContext

class CloudPipeline(userConnection: UserConnection) : ProtocolPipeline(userConnection) {
    override fun registerPackets() {
        super.registerPackets()
        add(CloudHeadProtocol) // add() will add tail protocol
    }

    override fun add(protocol: Protocol<*, *, *, *>?) {
        super.add(protocol)
        pipes().removeIf { it == CloudHeadProtocol }
        pipes().add(0, CloudHeadProtocol)
        pipes().removeIf { it == CloudTailProtocol }
        pipes().add(CloudTailProtocol)
    }
}

object CloudHeadProtocol : SimpleProtocol() {
    val logger = Logger.getLogger("CloudHandlerProtocol")
    override fun registerPackets() {
        this.registerIncoming(State.HANDSHAKE, 0, 0, object : PacketRemapper() {
            override fun registerMap() {
                handler { wrapper: PacketWrapper ->
                    val playerVer = wrapper.passthrough(Type.VAR_INT)
                    val addr = wrapper.passthrough(Type.STRING) // Server Address
                    val receivedPort = wrapper.passthrough(Type.UNSIGNED_SHORT)
                    val nextState = wrapper.passthrough(Type.VAR_INT)

                    val parsed = VIAaaSAddress().parse(addr, VIAaaSConfig.hostName)
                    if (parsed.port == 0) {
                        parsed.port = receivedPort
                    }

                    logger.info("connecting ${wrapper.user().channel!!.remoteAddress()} ($playerVer) to ${parsed.realAddress}:${parsed.port} (${parsed.protocol})")

                    wrapper.user().channel!!.setAutoRead(false)
                    wrapper.user().put(CloudData(
                            backendVer = parsed.protocol,
                            userConnection = wrapper.user(),
                            frontOnline = parsed.online
                    ))

                    Via.getPlatform().runAsync {
                        val frontForwarder = wrapper.user().channel!!.pipeline().get(CloudSideForwarder::class.java)
                        try {
                            var srvResolvedAddr = parsed.realAddress
                            var srvResolvedPort = parsed.port
                            if (srvResolvedPort == 25565) {
                                try {
                                    // https://github.com/GeyserMC/Geyser/blob/99e72f35b308542cf0dbfb5b58816503c3d6a129/connector/src/main/java/org/geysermc/connector/GeyserConnector.java
                                    val ctx = InitialDirContext()
                                    val attr = ctx.getAttributes("dns:///_minecraft._tcp.${parsed.realAddress}", arrayOf("SRV"))["SRV"]
                                    if (attr != null && attr.size() > 0) {
                                        val record = (attr.get(0) as String).split(" ").toTypedArray()
                                        srvResolvedAddr = record[3]
                                        srvResolvedPort = record[2].toInt()
                                    }
                                } catch (ignored: NameNotFoundException) {
                                }
                            }
                            val socketAddr = InetSocketAddress(InetAddress.getByName(srvResolvedAddr), srvResolvedPort)
                            val addrInfo = socketAddr.address
                            if (addrInfo.isSiteLocalAddress
                                    || addrInfo.isLoopbackAddress
                                    || addrInfo.isLinkLocalAddress
                                    || addrInfo.isAnyLocalAddress) throw SecurityException("Local addresses aren't allowed")
                            val bootstrap = Bootstrap().handler(BackendInit(wrapper.user()))
                                    .channel(NioSocketChannel::class.java)
                                    .group(wrapper.user().channel!!.eventLoop())
                                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000) // Half of mc timeout
                                    .connect(socketAddr)

                            bootstrap.addListener {
                                if (it.isSuccess) {
                                    logger.info("conected ${wrapper.user().channel?.remoteAddress()} to $socketAddr")
                                    val chann = bootstrap.channel() as SocketChannel
                                    chann.pipeline().get(CloudSideForwarder::class.java).other = wrapper.user().channel
                                    frontForwarder.other = chann
                                    val backHandshake = ByteBufAllocator.DEFAULT.buffer()
                                    try {
                                        val nullParts = addr.split(0.toChar())
                                        backHandshake.writeByte(0) // Packet 0 handshake
                                        val connProto = if (ProtocolRegistry.getProtocolPath(playerVer, parsed.protocol) != null) parsed.protocol else playerVer
                                        Type.VAR_INT.writePrimitive(backHandshake, connProto)
                                        Type.STRING.write(backHandshake, srvResolvedAddr + (if (nullParts.size == 2) 0.toChar() + nullParts[1] else "")) // Server Address
                                        backHandshake.writeShort(srvResolvedPort)
                                        Type.VAR_INT.writePrimitive(backHandshake, nextState)
                                        chann.writeAndFlush(backHandshake.retain())
                                    } finally {
                                        backHandshake.release()
                                    }
                                    wrapper.user().channel!!.setAutoRead(true)
                                } else {
                                    wrapper.user().channel!!.eventLoop().submit {
                                        frontForwarder.disconnect("Couldn't connect: " + it.cause().toString())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            wrapper.user().channel!!.eventLoop().submit {
                                frontForwarder.disconnect("Couldn't connect: $e")
                            }
                        }
                    }
                }
            }
        })
    }
}

object CloudTailProtocol : SimpleProtocol() {
    override fun registerPackets() {
        // Login start
        this.registerIncoming(State.LOGIN, 0, 0, object : PacketRemapper() {
            override fun registerMap() {
                handler {
                    it.user().get(CloudData::class.java)!!.frontLoginName = it.passthrough(Type.STRING)
                }
            }
        })

        this.registerOutgoing(State.LOGIN, 3, 3, object : PacketRemapper() {
            // set compression
            override fun registerMap() {
                handler {
                    val pipe = it.user().channel!!.pipeline()
                    val threshold = it.read(Type.VAR_INT)
                    it.cancel()
                    it.create(3) {
                        it.write(Type.VAR_INT, threshold)
                    }.send(CloudTailProtocol::class.java, true, true) // needs to be sent uncompressed
                    pipe.get(CloudCompressor::class.java).threshold = threshold
                    pipe.get(CloudDecompressor::class.java).threshold = threshold

                    val backPipe = pipe.get(CloudSideForwarder::class.java).other!!.pipeline()
                    backPipe.get(CloudCompressor::class.java)?.threshold = threshold
                    backPipe.get(CloudDecompressor::class.java)?.threshold = threshold
                }
            }
        })

        // Crypto request
        this.registerOutgoing(State.LOGIN, 1, 1, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING) // Server id - unused
                map(Type.BYTE_ARRAY_PRIMITIVE) // Public key
                map(Type.BYTE_ARRAY_PRIMITIVE) // Token
                handler {
                    val data = it.user().get(CloudData::class.java)!!
                    data.backServerId = it.get(Type.STRING, 0)
                    data.backPublicKey = KeyFactory.getInstance("RSA")
                            .generatePublic(X509EncodedKeySpec(it.get(Type.BYTE_ARRAY_PRIMITIVE, 0)))
                    data.backToken = it.get(Type.BYTE_ARRAY_PRIMITIVE, 1)

                    // We'll use non-vanilla server id, public key size and token size
                    it.set(Type.STRING, 0, "VIAaaS")
                    it.set(Type.BYTE_ARRAY_PRIMITIVE, 0, mcCryptoKey.public.encoded)
                    val token = ByteArray(16)
                    secureRandom.nextBytes(token)
                    data.frontToken = token
                    it.set(Type.BYTE_ARRAY_PRIMITIVE, 1, token.clone())
                }
            }
        })

        this.registerIncoming(State.LOGIN, 1, 1, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.BYTE_ARRAY_PRIMITIVE) // RSA shared secret
                map(Type.BYTE_ARRAY_PRIMITIVE) // RSA token - wat??? why is it encrypted with RSA if it was sent unencrypted?
                handler { wrapper ->
                    val data = wrapper.user().get(CloudData::class.java)!!

                    val encryptedSecret = wrapper.get(Type.BYTE_ARRAY_PRIMITIVE, 0)
                    val secret = Cipher.getInstance("RSA").let {
                        it.init(Cipher.DECRYPT_MODE, mcCryptoKey.private)
                        it.doFinal(encryptedSecret)
                    }
                    val encryptedToken = wrapper.get(Type.BYTE_ARRAY_PRIMITIVE, 1)
                    val decryptedToken = Cipher.getInstance("RSA").let {
                        it.init(Cipher.DECRYPT_MODE, mcCryptoKey.private)
                        it.doFinal(encryptedToken)
                    }!!

                    if (!decryptedToken.contentEquals(data.frontToken!!)) throw IllegalStateException("invalid token!")

                    val spec = SecretKeySpec(secret, "AES")
                    val iv = IvParameterSpec(secret)

                    val aesEn = Cipher.getInstance("AES/CFB8/NoPadding")
                    val aesDe = Cipher.getInstance("AES/CFB8/NoPadding")
                    aesEn.init(Cipher.ENCRYPT_MODE, spec, iv)
                    aesDe.init(Cipher.DECRYPT_MODE, spec, iv)

                    wrapper.user().channel!!.pipeline().get(CloudEncryptor::class.java).cipher = aesEn
                    wrapper.user().channel!!.pipeline().get(CloudDecryptor::class.java).cipher = aesDe

                    val frontHash = generateServerHash("VIAaaS", secret, mcCryptoKey.public)

                    val backKey = ByteArray(16)
                    secureRandom.nextBytes(backKey)

                    val backSpec = SecretKeySpec(secret, "AES")
                    val backIv = IvParameterSpec(secret)

                    val backAesEn = Cipher.getInstance("AES/CFB8/NoPadding")
                    val backAesDe = Cipher.getInstance("AES/CFB8/NoPadding")
                    backAesEn.init(Cipher.ENCRYPT_MODE, backSpec, backIv)
                    backAesDe.init(Cipher.DECRYPT_MODE, backSpec, backIv)

                    val backHash = generateServerHash(data.backServerId!!, backKey, data.backPublicKey!!)

                    wrapper.cancel()
                    Via.getPlatform().runAsync {
                        // Don't need to disable autoread, server will wait us
                        runBlocking {
                            try {
                                val profile = httpClient.get<JsonObject?>("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                                        "${UrlEscapers.urlFormParameterEscaper().escape(data.frontLoginName!!)}&serverId=$frontHash")
                                        ?: throw IllegalArgumentException("Couldn't authenticate with session servers")

                                var sent = false
                                viaWebServer.listeners[fromUndashed(profile.get("id")!!.asString)]?.forEach {
                                    it.ws.send("""{"action": "session_hash_request", "session_hash": "$backHash",
                                        | "client_address": "${wrapper.user().channel!!.remoteAddress()}", "backend_public_key":
                                        |  "${Base64.getEncoder().encodeToString(data.backPublicKey!!.encoded)}"}""".trimMargin())
                                    it.ws.flush()
                                    sent = true
                                }

                                if (!sent) {
                                    throw IllegalStateException("No connection to browser, connect in /auth.html")
                                } else {
                                    viaWebServer.pendingSessionHashes.get(backHash).get(15, TimeUnit.SECONDS)
                                    wrapper.user().channel!!.eventLoop().submit {
                                        val backCrypto = ByteBufAllocator.DEFAULT.buffer()
                                        try {
                                            backCrypto.writeByte(1) // Packet id
                                            Type.BYTE_ARRAY_PRIMITIVE.write(backCrypto, Cipher.getInstance("RSA").let {
                                                it.init(Cipher.ENCRYPT_MODE, data.backPublicKey)
                                                it.doFinal(backKey)
                                            })
                                            Type.BYTE_ARRAY_PRIMITIVE.write(backCrypto, Cipher.getInstance("RSA").let {
                                                it.init(Cipher.ENCRYPT_MODE, data.backPublicKey)
                                                it.doFinal(data.backToken)
                                            })
                                            val backChan = wrapper.user().channel!!.pipeline()
                                                    .get(CloudSideForwarder::class.java).other!!
                                            backChan.writeAndFlush(backCrypto.retain())
                                            backChan.pipeline().get(CloudEncryptor::class.java).cipher = backAesEn
                                            backChan.pipeline().get(CloudDecryptor::class.java).cipher = backAesDe
                                        } finally {
                                            backCrypto.release()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                wrapper.user().channel!!.pipeline()
                                        .get(CloudSideForwarder::class.java)
                                        .disconnect("Online mode error: $e")
                            }
                        }
                    }
                }
            }
        })
    }
}

fun Channel.setAutoRead(b: Boolean) {
    this.config().isAutoRead = b
    if (b) this.read()
}

val secureRandom = SecureRandom.getInstanceStrong()

fun twosComplementHexdigest(digest: ByteArray): String {
    return BigInteger(digest).toString(16)
}

// https://github.com/VelocityPowered/Velocity/blob/0dd6fe1ef2783fe1f9322af06c6fd218aa67cdb1/proxy/src/main/java/com/velocitypowered/proxy/util/EncryptionUtils.java
fun generateServerHash(serverId: String, sharedSecret: ByteArray?, key: PublicKey): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(serverId.toByteArray(Charsets.ISO_8859_1))
    digest.update(sharedSecret)
    digest.update(key.encoded)
    return twosComplementHexdigest(digest.digest())
}

data class CloudData(val userConnection: UserConnection,
                     var backendVer: Int,
                     var frontOnline: Boolean,
                     var pendingStatus: Boolean = false,
                     var backServerId: String? = null,
                     var backPublicKey: PublicKey? = null,
                     var backToken: ByteArray? = null,
                     var frontToken: ByteArray? = null,
                     var frontLoginName: String? = null
) : StoredObject(userConnection)