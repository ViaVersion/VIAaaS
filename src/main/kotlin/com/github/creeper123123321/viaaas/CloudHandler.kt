package com.github.creeper123123321.viaaas

import com.google.common.net.UrlEscapers
import com.google.gson.Gson
import com.google.gson.JsonObject
import de.gerrygames.viarewind.netty.EmptyChannelHandler
import io.ktor.client.request.*
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelOption
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.SocketChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.type.Type
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
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.naming.NameNotFoundException
import javax.naming.directory.InitialDirContext

val chLogger = LoggerFactory.getLogger("VIAaaS MC Handler")

class HandlerData(
    userConnection: UserConnection,
    var state: MinecraftConnectionState,
    var protocolId: Int? = null,
    var frontName: String? = null,
    var backName: String? = null,
    var backServerId: String? = null,
    var backPublicKey: PublicKey? = null,
    var backToken: ByteArray? = null,
    var frontToken: ByteArray? = null,
    var frontId: String? = null
) : StoredObject(userConnection)

class CloudMinecraftHandler(
    val user: UserConnection,
    var other: Channel?,
    val frontEnd: Boolean
) : SimpleChannelInboundHandler<ByteBuf>() {
    val data get() = user.get(HandlerData::class.java)!!
    var address: SocketAddress? = null

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (ctx.channel().isActive && !user.isPendingDisconnect && msg.isReadable) {
            data.state.handleMessage(this, ctx, msg)
            if (msg.isReadable) throw IllegalStateException("Remaining bytes!!!")
            //other?.write(msg.retain())
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        address = ctx.channel().remoteAddress()
        if (user.get(HandlerData::class.java) == null) {
            user.put(HandlerData(user, HandshakeState()))
        }
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
        disconnect("Exception: $cause")
        chLogger.debug("Exception: ", cause)
    }

    fun disconnect(s: String) {
        data.state.disconnect(this, s)
    }
}

interface MinecraftConnectionState {
    fun handleMessage(
        handler: CloudMinecraftHandler, ctx: ChannelHandlerContext,
        msg: ByteBuf
    )

    fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        chLogger.info("Disconnected ${handler.address}: $msg")
        handler.user.isPendingDisconnect = true
    }

    fun onInactivated(handler: CloudMinecraftHandler) {
        chLogger.info(handler.address?.toString() + " inactivated")
    }
}

class HandshakeState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        val packet = PacketRegistry.decode(
            msg,
            ProtocolVersion.getProtocol(handler.user.protocolInfo!!.serverProtocolVersion),
            State.HANDSHAKE,
            handler.frontEnd
        )
        if (packet !is HandshakePacket) throw IllegalArgumentException("Invalid packet!")
        handler.data.protocolId = packet.protocolId
        when (packet.nextState.ordinal) {
            1 -> handler.data.state = StatusState
            2 -> handler.data.state = LoginState
            else -> throw IllegalStateException("Invalid next state")
        }

        if (!handler.user.get(CloudData::class.java)!!.hadHostname && VIAaaSConfig.requireHostName) {
            throw UnsupportedOperationException("This VIAaaS instance requires you to use the hostname")
        }

        handler.user.channel!!.setAutoRead(false)
        GlobalScope.launch(Dispatchers.IO) {
            val frontHandler = handler.user.channel!!.pipeline().get(CloudMinecraftHandler::class.java)
            try {
                var srvResolvedAddr = packet.address
                var srvResolvedPort = packet.port
                if (srvResolvedPort == 25565) {
                    try {
                        // https://github.com/GeyserMC/Geyser/blob/99e72f35b308542cf0dbfb5b58816503c3d6a129/connector/src/main/java/org/geysermc/connector/GeyserConnector.java
                        val attr = InitialDirContext()
                            .getAttributes("dns:///_minecraft._tcp.$srvResolvedAddr", arrayOf("SRV"))["SRV"]
                        if (attr != null && attr.size() > 0) {
                            val record = (attr.get(0) as String).split(" ")
                            srvResolvedAddr = record[3]
                            srvResolvedPort = record[2].toInt()
                        }
                    } catch (ignored: NameNotFoundException) {
                    }
                }
                val socketAddr = InetSocketAddress(InetAddress.getByName(srvResolvedAddr), srvResolvedPort)
                val addrInfo = socketAddr.address
                if (VIAaaSConfig.blockLocalAddress && (addrInfo.isSiteLocalAddress
                            || addrInfo.isLoopbackAddress
                            || addrInfo.isLinkLocalAddress
                            || addrInfo.isAnyLocalAddress)
                ) throw SecurityException("Local addresses aren't allowed")

                val bootstrap = Bootstrap().handler(BackendInit(handler.user))
                    .channelFactory(channelSocketFactory())
                    .group(handler.user.channel!!.eventLoop())
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000) // Half of mc timeout
                    .connect(socketAddr)

                bootstrap.addListener {
                    if (it.isSuccess) {
                        CloudHeadProtocol.logger.info("Connected ${frontHandler.address} -> $socketAddr")

                        val backChan = bootstrap.channel() as SocketChannel
                        backChan.pipeline().get(CloudMinecraftHandler::class.java).other = handler.user.channel
                        frontHandler.other = backChan

                        packet.address = srvResolvedAddr
                        packet.port = srvResolvedPort
                        forward(handler, packet)
                        backChan.flush()

                        handler.user.channel!!.setAutoRead(true)
                    } else {
                        // We're in the event loop
                        frontHandler.disconnect("Couldn't connect: " + it.cause().toString())
                    }
                }
            } catch (e: Exception) {
                handler.user.channel!!.eventLoop().submit {
                    frontHandler.disconnect("Couldn't connect: $e")
                }
            }
        }
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        handler.user.channel?.close() // Not worth logging
    }

    override fun onInactivated(handler: CloudMinecraftHandler) {
        // Not worth logging
    }
}

object LoginState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        val packet = PacketRegistry.decode(
            msg,
            ProtocolVersion.getProtocol(handler.user.protocolInfo!!.serverProtocolVersion),
            State.LOGIN,
            handler.frontEnd
        )

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
        val pipe = handler.user.channel!!.pipeline()
        val threshold = setCompression.threshold

        val backPipe = pipe.get(CloudMinecraftHandler::class.java).other!!.pipeline()
        backPipe.addAfter("frame", "compress", CloudCompressionCodec(threshold))

        forward(handler, setCompression)

        pipe.addAfter("frame", "compress", CloudCompressionCodec(threshold))
        pipe.addAfter("frame", "decompress", EmptyChannelHandler()) // ViaRewind compat workaround
    }

    fun handleCryptoRequest(handler: CloudMinecraftHandler, cryptoRequest: CryptoRequest) {
        val data = handler.data
        data.backServerId = cryptoRequest.serverId
        data.backPublicKey = cryptoRequest.publicKey
        data.backToken = cryptoRequest.token

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
        data.frontToken = token
        data.frontId = id

        cryptoRequest.serverId = id
        cryptoRequest.publicKey = mcCryptoKey.public
        cryptoRequest.token = token

        forward(handler, cryptoRequest)
    }

    fun handleCryptoResponse(handler: CloudMinecraftHandler, cryptoResponse: CryptoResponse) {
        val frontHash = let {
            val frontKey = decryptRsa(mcCryptoKey.private, cryptoResponse.encryptedKey)
            // RSA token - wat??? why is it encrypted with RSA if it was sent unencrypted?
            val decryptedToken = decryptRsa(mcCryptoKey.private, cryptoResponse.encryptedToken)

            if (!decryptedToken.contentEquals(handler.data.frontToken!!)) throw IllegalStateException("invalid token!")

            val aesEn = mcCfb8(frontKey, Cipher.ENCRYPT_MODE)
            val aesDe = mcCfb8(frontKey, Cipher.DECRYPT_MODE)

            handler.user.channel!!.pipeline().addBefore("frame", "crypto", CloudCrypto(aesDe, aesEn))

            generateServerHash(handler.data.frontId!!, frontKey, mcCryptoKey.public)
        }

        val backKey = ByteArray(16).let {
            secureRandom.nextBytes(it)
            it
        }

        val backHash = generateServerHash(handler.data.backServerId!!, backKey, handler.data.backPublicKey!!)

        handler.user.channel!!.setAutoRead(false)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val profile = httpClient.get<JsonObject?>(
                    "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                            "${
                                UrlEscapers.urlFormParameterEscaper().escape(handler.data.frontName!!)
                            }&serverId=$frontHash"
                )
                    ?: throw IllegalArgumentException("Couldn't authenticate with session servers")

                val sessionJoin = viaWebServer.requestSessionJoin(
                    fromUndashed(profile.get("id")!!.asString),
                    handler.data.backName!!,
                    backHash,
                    handler.address!!, // Frontend handler
                    handler.data.backPublicKey!!
                )

                val backChan = handler.other!!
                sessionJoin.whenCompleteAsync({ _, throwable ->
                    if (throwable != null) {
                        handler.disconnect("Online mode error: $throwable")
                    } else {
                        cryptoResponse.encryptedKey = encryptRsa(handler.data.backPublicKey!!, backKey)
                        cryptoResponse.encryptedToken =
                            encryptRsa(handler.data.backPublicKey!!, handler.data.backToken!!)
                        forward(handler, cryptoResponse)
                        backChan.flush()

                        val backAesEn = mcCfb8(backKey, Cipher.ENCRYPT_MODE)
                        val backAesDe = mcCfb8(backKey, Cipher.DECRYPT_MODE)
                        backChan.pipeline().addBefore("frame", "crypto", CloudCrypto(backAesDe, backAesEn))
                    }
                }, backChan.eventLoop())
            } catch (e: Exception) {
                handler.disconnect("Online mode error: $e")
            }
            handler.user.channel!!.setAutoRead(true)
        }
    }

    fun handleLoginStart(handler: CloudMinecraftHandler, loginStart: LoginStart) {
        handler.data.frontName = loginStart.username
        handler.data.backName = handler.user.get(CloudData::class.java)!!.altName ?: handler.data.frontName

        loginStart.username = handler.data.backName!!
        forward(handler, loginStart)
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = ByteBufAllocator.DEFAULT.buffer()
        try {
            packet.writeByte(0) // id 0 disconnect
            Type.STRING.write(packet, Gson().toJson("[VIAaaS] §c$msg"))
            handler.user
                .sendRawPacketFuture(packet.retain())
                .addListener { handler.user.channel?.close() }
        } finally {
            packet.release()
        }
    }
}

object StatusState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        val i = msg.readerIndex()
        if (Type.VAR_INT.readPrimitive(msg) !in 0..1) throw IllegalArgumentException("Invalid packet id!")
        msg.readerIndex(i)
        handler.other!!.write(msg.retain())
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = ByteBufAllocator.DEFAULT.buffer()
        try {
            packet.writeByte(0) // id 0 disconnect
            Type.STRING.write(
                packet, """{"version": {"name": "VIAaaS", "protocol": -1}, "players":
| {"max": 0, "online": 0, "sample": []}, "description": {"text": ${Gson().toJson("§c$msg")}}}""".trimMargin()
            )
            handler.user.sendRawPacketFuture(packet.retain())
                .addListener { handler.user.channel?.close() }
        } finally {
            packet.release()
        }
    }
}

object PlayState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        val i = msg.readerIndex()
        if (Type.VAR_INT.readPrimitive(msg) !in 0..127) throw IllegalArgumentException("Invalid packet id!")
        msg.readerIndex(i)
        handler.other!!.write(msg.retain())
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        super.disconnect(handler, msg)
        handler.user.channel?.close()
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

private fun forward(handler: CloudMinecraftHandler, packet: Packet) {
    val msg = ByteBufAllocator.DEFAULT.buffer()
    try {
        PacketRegistry.encode(packet, msg, ProtocolVersion.getProtocol(handler.data.protocolId!!))
        handler.other!!.write(msg.retain())
    } finally {
        msg.release()
    }
}