package com.github.creeper123123321.viaaas

import com.google.common.net.UrlEscapers
import com.google.common.primitives.Ints
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.request.*
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.socket.SocketChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.exception.CancelCodecException
import us.myles.ViaVersion.packets.State
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.naming.NameNotFoundException
import javax.naming.directory.InitialDirContext


val mcLogger = LoggerFactory.getLogger("VIAaaS MC")

class ConnectionData(
    val frontChannel: Channel,
    var backChannel: Channel? = null,
    var state: MinecraftConnectionState = HandshakeState(),
    var frontOnline: Boolean? = null, // todo
    var frontName: String? = null,
    var backName: String? = null,
    var frontVer: Int? = null,
    var backVer: Int? = null,
) {
    val frontHandler get() = frontChannel.pipeline().get(CloudMinecraftHandler::class.java)
    val backHandler get() = backChannel?.pipeline()?.get(CloudMinecraftHandler::class.java)
}

class CloudMinecraftHandler(
    val data: ConnectionData,
    var other: Channel?,
    val frontEnd: Boolean
) : SimpleChannelInboundHandler<Packet>() {
    var remoteAddress: SocketAddress? = null

    override fun channelRead0(ctx: ChannelHandlerContext, packet: Packet) {
        if (ctx.channel().isActive) {
            data.state.handlePacket(this, ctx, packet)
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        remoteAddress = ctx.channel().remoteAddress()
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        other?.close()
        data.state.onInactivated(this)
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext?) {
        other?.flush()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        other?.setAutoRead(ctx.channel().isWritable)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (cause is CancelCodecException) return
        mcLogger.debug("Exception: ", cause)
        disconnect("Exception: $cause")
    }

    fun disconnect(s: String) {
        data.state.disconnect(this, s)
    }
}

interface MinecraftConnectionState {
    val state: State
    fun handlePacket(
        handler: CloudMinecraftHandler, ctx: ChannelHandlerContext,
        packet: Packet
    )

    fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        mcLogger.info("Disconnected ${handler.remoteAddress}: $msg")
    }

    fun onInactivated(handler: CloudMinecraftHandler) {
        mcLogger.info(handler.remoteAddress?.toString() + " inactivated")
    }
}

class HandshakeState : MinecraftConnectionState {
    fun connectBack(handler: CloudMinecraftHandler, socketAddr: InetSocketAddress): ChannelFuture {
        return Bootstrap()
            .handler(BackendInit(handler.data))
            .channelFactory(channelSocketFactory())
            .group(handler.data.frontChannel.eventLoop())
            .option(ChannelOption.IP_TOS, 0x18)
            .option(ChannelOption.TCP_NODELAY, true)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000) // Half of mc timeout
            .connect(socketAddr)
    }

    override val state: State
        get() = State.HANDSHAKE

    override fun handlePacket(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet !is HandshakePacket) throw IllegalArgumentException("Invalid packet!")

        handler.data.frontVer = packet.protocolId
        when (packet.nextState.ordinal) {
            1 -> handler.data.state = StatusState
            2 -> handler.data.state = LoginState()
            else -> throw IllegalStateException("Invalid next state")
        }

        val parsed = VIAaaSAddress().parse(packet.address.substringBefore(0.toChar()), VIAaaSConfig.hostName)
        val backProto = parsed.protocol ?: 47 // todo autodetection
        val hadHostname = parsed.viaSuffix != null

        packet.address = parsed.realAddress!!
        packet.port = parsed.port ?: if (VIAaaSConfig.defaultBackendPort == -1) {
            packet.port
        } else {
            VIAaaSConfig.defaultBackendPort
        }

        handler.data.backVer = backProto
        handler.data.frontOnline = parsed.online
        handler.data.backName = parsed.altUsername

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

                        val backChan = future.channel() as SocketChannel
                        handler.data.backChannel = backChan
                        handler.other = backChan

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

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        handler.data.frontChannel.close() // Not worth logging
    }

    override fun onInactivated(handler: CloudMinecraftHandler) {
        // Not worth logging
    }
}

class LoginState : MinecraftConnectionState {
    val callbackPlayerId = CompletableFuture<String>()
    lateinit var frontToken: ByteArray
    lateinit var frontServerId: String
    override val state: State
        get() = State.LOGIN

    override fun handlePacket(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        when (packet) {
            is LoginStart -> handleLoginStart(handler, packet)
            is CryptoResponse -> handleCryptoResponse(handler, packet)
            is PluginResponse -> forward(handler, packet)
            is LoginDisconnect -> forward(handler, packet)
            is CryptoRequest -> handleCryptoRequest(handler, packet)
            is LoginSuccess -> handleLoginSuccess(handler, packet)
            is SetCompression -> handleCompression(handler, packet)
            is PluginRequest -> forward(handler, packet)
            else -> throw IllegalArgumentException("Invalid packet!")
        }
    }

    private fun handleLoginSuccess(handler: CloudMinecraftHandler, loginSuccess: LoginSuccess) {
        handler.data.state = PlayState
        forward(handler, loginSuccess)
    }

    private fun handleCompression(handler: CloudMinecraftHandler, setCompression: SetCompression) {
        val pipe = handler.data.frontChannel.pipeline()
        val threshold = setCompression.threshold

        val backPipe = pipe.get(CloudMinecraftHandler::class.java).other!!.pipeline()
        if (threshold != -1) {
            backPipe.addAfter("frame", "compress", CloudCompressionCodec(threshold))
        } else if (backPipe.get("compress") != null) {
            backPipe.remove("compress")
        }

        forward(handler, setCompression)

        if (threshold != -1) {
            pipe.addAfter("frame", "compress", CloudCompressionCodec(threshold))
            // todo viarewind backend compression
        } else if (pipe.get("compress") != null) {
            pipe.remove("compress")
        }
    }

    fun authenticateOnlineFront(frontHandler: CloudMinecraftHandler) {
        val id = "VIAaaS" + ByteArray(10).let {
            secureRandom.nextBytes(it)
            Base64.getEncoder().withoutPadding().encodeToString(it)
            // https://developer.mozilla.org/en-US/docs/Glossary/Base64 133% of original
        }
        // We'll use non-vanilla server id, public key size and token size
        val token = ByteArray(16).let {
            secureRandom.nextBytes(it)
            it
        }
        frontToken = token
        frontServerId = id

        val cryptoRequest = CryptoRequest()
        cryptoRequest.serverId = id
        cryptoRequest.publicKey = mcCryptoKey.public
        cryptoRequest.token = token

        sendPacket(frontHandler.data.frontChannel, cryptoRequest, true)
    }

    fun handleCryptoRequest(handler: CloudMinecraftHandler, cryptoRequest: CryptoRequest) {
        val data = handler.data
        val backServerId = cryptoRequest.serverId
        val backPublicKey = cryptoRequest.publicKey
        val backToken = cryptoRequest.token

        if (data.frontOnline == null) {
            authenticateOnlineFront(handler)
        }

        val backKey = ByteArray(16).let {
            secureRandom.nextBytes(it)
            it
        }
        val backHash = generateServerHash(backServerId, backKey, backPublicKey)

        callbackPlayerId.whenComplete { playerId, e ->
            if (e != null) return@whenComplete
            val frontHandler = handler.data.frontHandler
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val sessionJoin = viaWebServer.requestSessionJoin(
                        parseUndashedId(playerId),
                        handler.data.backName!!,
                        backHash,
                        frontHandler.remoteAddress!!, // Frontend handler
                        backPublicKey
                    )

                    val backChan = handler.data.backChannel!!
                    sessionJoin.whenCompleteAsync({ _, throwable ->
                        if (throwable != null) {
                            frontHandler.data.backHandler!!.disconnect("Online mode error: $throwable")
                        } else {
                            val cryptoResponse = CryptoResponse()
                            cryptoResponse.encryptedKey = encryptRsa(backPublicKey, backKey)
                            cryptoResponse.encryptedToken = encryptRsa(backPublicKey, backToken)
                            forward(frontHandler, cryptoResponse, true)

                            val backAesEn = mcCfb8(backKey, Cipher.ENCRYPT_MODE)
                            val backAesDe = mcCfb8(backKey, Cipher.DECRYPT_MODE)
                            backChan.pipeline().addBefore("frame", "crypto", CloudCrypto(backAesDe, backAesEn))
                        }
                    }, backChan.eventLoop())
                } catch (e: Exception) {
                    frontHandler.disconnect("Online mode error: $e")
                }
            }
        }
    }

    fun handleCryptoResponse(handler: CloudMinecraftHandler, cryptoResponse: CryptoResponse) {
        val frontHash = let {
            val frontKey = decryptRsa(mcCryptoKey.private, cryptoResponse.encryptedKey)
            // RSA token - wat??? why is it encrypted with RSA if it was sent unencrypted?
            val decryptedToken = decryptRsa(mcCryptoKey.private, cryptoResponse.encryptedToken)

            if (!decryptedToken.contentEquals(frontToken)) throw IllegalStateException("invalid token!")

            val aesEn = mcCfb8(frontKey, Cipher.ENCRYPT_MODE)
            val aesDe = mcCfb8(frontKey, Cipher.DECRYPT_MODE)

            handler.data.frontChannel.pipeline().addBefore("frame", "crypto", CloudCrypto(aesDe, aesEn))

            generateServerHash(frontServerId, frontKey, mcCryptoKey.public)
        }

        handler.data.frontChannel.setAutoRead(false)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val profile = httpClient.get<JsonObject?>(
                    "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                            UrlEscapers.urlFormParameterEscaper().escape(handler.data.frontName!!) +
                            "&serverId=$frontHash"
                ) ?: throw IllegalArgumentException("Couldn't authenticate with session servers")

                val id = profile.get("id")!!.asString
                mcLogger.info("Validated front-end session: ${handler.data.frontName} $id")
                callbackPlayerId.complete(id)
            } catch (e: Exception) {
                callbackPlayerId.completeExceptionally(e)
            }
            handler.data.frontChannel.setAutoRead(true)
        }
    }

    fun handleLoginStart(handler: CloudMinecraftHandler, loginStart: LoginStart) {
        if (loginStart.username.length > 16) throw badLength

        handler.data.frontName = loginStart.username
        handler.data.backName = handler.data.backName ?: handler.data.frontName

        loginStart.username = handler.data.backName!!

        callbackPlayerId.whenComplete { _, e -> if (e != null) disconnect(handler, "Profile error: $e") }

        if (handler.data.frontOnline == false) {
            callbackPlayerId.complete(generateOfflinePlayerUuid(handler.data.frontName!!).toString().replace("-", ""))
        }

        if (handler.data.frontOnline == true) { // forced
            authenticateOnlineFront(handler.data.backHandler!!)
            callbackPlayerId.whenComplete { _, e ->
                if (e == null) forward(handler, loginStart, true)
            }
        } else {
            forward(handler, loginStart)
        }
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = LoginDisconnect()
        packet.msg = Gson().toJson("[VIAaaS] §c$msg")
        sendFlushPacketClose(handler.data.frontChannel, packet)
    }
}

object StatusState : MinecraftConnectionState {
    override val state: State
        get() = State.STATUS

    override fun handlePacket(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if (packet is UnknownPacket) throw IllegalArgumentException("Invalid packet")
        forward(handler, packet)
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = StatusResponse()
        packet.json = """{"version": {"name": "VIAaaS", "protocol": -1}, "players": {"max": 0, "online": 0,
            | "sample": []}, "description": {"text": ${Gson().toJson("§c$msg")}}}""".trimMargin()
        sendFlushPacketClose(handler.data.frontChannel, packet)
    }
}

object PlayState : MinecraftConnectionState {
    override val state: State
        get() = State.PLAY

    override fun handlePacket(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        if ((packet as UnknownPacket).id !in 0..127) throw IllegalArgumentException("Invalid packet id!")
        forward(handler, packet)
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.data.frontChannel.close()
    }
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


fun Channel.setAutoRead(b: Boolean) {
    this.config().isAutoRead = b
    if (b) this.read()
}

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

private fun sendFlushPacketClose(ch: Channel, packet: Packet) {
    ch.writeAndFlush(packet).addListener { ch.close() }
}

private fun forward(handler: CloudMinecraftHandler, packet: Packet, flush: Boolean = false) {
    sendPacket(handler.other!!, packet, flush)
}

private fun sendPacket(ch: Channel, packet: Packet, flush: Boolean = false) {
    if (flush) {
        ch.writeAndFlush(packet, ch.voidPromise())
    } else {
        ch.write(packet, ch.voidPromise())
    }
}

private fun resolveSrv(address: String, port: Int): Pair<String, Int> {
    if (port == 25565) {
        try {
            // https://github.com/GeyserMC/Geyser/blob/99e72f35b308542cf0dbfb5b58816503c3d6a129/connector/src/main/java/org/geysermc/connector/GeyserConnector.java
            val attr = InitialDirContext()
                .getAttributes("dns:///_minecraft._tcp.$address", arrayOf("SRV"))["SRV"]
            if (attr != null && attr.size() > 0) {
                val record = (attr.get(0) as String).split(" ")
                return record[3] to record[2].toInt()
            }
        } catch (ignored: NameNotFoundException) {
        }
    }
    return address to port
}

private fun checkLocalAddress(inetAddress: InetAddress): Boolean {
    return VIAaaSConfig.blockLocalAddress && (inetAddress.isSiteLocalAddress
            || inetAddress.isLoopbackAddress
            || inetAddress.isLinkLocalAddress
            || inetAddress.isAnyLocalAddress)
}

private fun matchesAddress(addr: InetSocketAddress, list: List<String>): Boolean {
    return (matchAddress(addr.hostString, list)
            || (addr.address != null && (matchAddress(addr.address.hostAddress, list)
            || matchAddress(addr.address.hostName, list))))
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

// https://github.com/VelocityPowered/Velocity/blob/e3f17eeb245b8d570f16c1f2aff5e7eafb698d5e/api/src/main/java/com/velocitypowered/api/util/UuidUtils.java
fun generateOfflinePlayerUuid(username: String) =
    UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8))