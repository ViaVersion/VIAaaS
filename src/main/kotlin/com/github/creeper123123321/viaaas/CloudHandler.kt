package com.github.creeper123123321.viaaas

import com.google.common.net.UrlEscapers
import com.google.gson.Gson
import com.google.gson.JsonObject
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
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.exception.CancelCodecException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.naming.NameNotFoundException
import javax.naming.directory.InitialDirContext

val chLogger = LoggerFactory.getLogger("VIAaaS MC Handler")

class HandlerData(userConnection: UserConnection,
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

class CloudMinecraftHandler(val user: UserConnection,
                            var other: Channel?,
                            val frontEnd: Boolean) : SimpleChannelInboundHandler<ByteBuf>() {
    val data get() = user.get(HandlerData::class.java)
    var address: SocketAddress? = null

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (!user.isPendingDisconnect) {
            data!!.state.handleMessage(this, ctx, msg)
            if (msg.isReadable) throw IllegalStateException("Remaining bytes!!!")
            //other?.write(msg.retain())
        }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        address = ctx.channel().remoteAddress()
        if (data == null) {
            user.put(HandlerData(user, HandshakeState()))
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        chLogger.info(address?.toString() + " was disconnected")
        other?.close()
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
        cause.printStackTrace()
    }

    fun disconnect(s: String) {
        if (user.channel?.isActive != true) return

        chLogger.info("Disconnecting $address: $s")
        data!!.state.disconnect(this, s)
    }
}

interface MinecraftConnectionState {
    fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext,
                      msg: ByteBuf)

    fun disconnect(handler: CloudMinecraftHandler, msg: String)
}

class HandshakeState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (!handler.frontEnd || Type.VAR_INT.readPrimitive(msg) != 0) throw IllegalArgumentException("Invalid packet ID!")
        handler.data!!.protocolId = Type.VAR_INT.readPrimitive(msg)
        val backAddr = Type.STRING.read(msg)
        val backPort = Type.UNSIGNED_SHORT.read(msg)
        val nextAddr = Type.VAR_INT.readPrimitive(msg)
        when (nextAddr) {
            1 -> handler.data!!.state = StatusState()
            2 -> handler.data!!.state = LoginState()
            else -> throw IllegalStateException("Invalid next state")
        }

        handler.user.channel!!.setAutoRead(false)
        GlobalScope.launch(Dispatchers.IO) {
            val frontHandler = handler.user.channel!!.pipeline().get(CloudMinecraftHandler::class.java)
            try {
                var srvResolvedAddr = backAddr
                var srvResolvedPort = backPort
                if (srvResolvedPort == 25565) {
                    try {
                        // https://github.com/GeyserMC/Geyser/blob/99e72f35b308542cf0dbfb5b58816503c3d6a129/connector/src/main/java/org/geysermc/connector/GeyserConnector.java
                        val attr = InitialDirContext()
                                .getAttributes("dns:///_minecraft._tcp.$backAddr", arrayOf("SRV"))["SRV"]
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
                if (addrInfo.isSiteLocalAddress
                        || addrInfo.isLoopbackAddress
                        || addrInfo.isLinkLocalAddress
                        || addrInfo.isAnyLocalAddress) throw SecurityException("Local addresses aren't allowed")

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

                        val backHandshake = ByteBufAllocator.DEFAULT.buffer()
                        try {
                            backHandshake.writeByte(0) // Packet 0 handshake
                            Type.VAR_INT.writePrimitive(backHandshake, handler.data!!.protocolId!!)
                            Type.STRING.write(backHandshake, srvResolvedAddr) // Server Address
                            backHandshake.writeShort(srvResolvedPort)
                            Type.VAR_INT.writePrimitive(backHandshake, nextAddr)
                            backChan.writeAndFlush(backHandshake.retain())
                        } finally {
                            backHandshake.release()
                        }

                        handler.user.channel!!.setAutoRead(true)
                    } else {
                        handler.user.channel!!.eventLoop().submit {
                            frontHandler.disconnect("Couldn't connect: " + it.cause().toString())
                        }
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
        handler.user.disconnect(msg)
    }
}

class LoginState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        msg.markReaderIndex()
        val id = Type.VAR_INT.readPrimitive(msg)
        when {
            handler.frontEnd && id == 0 -> handleLoginStart(handler, msg)
            handler.frontEnd && id == 1 -> handleCryptoResponse(handler, msg)
            handler.frontEnd && id == 2 -> forward(handler, msg) // Plugin response
            !handler.frontEnd && id == 0 -> forward(handler, msg) // Disconnect
            !handler.frontEnd && id == 1 -> handleCryptoRequest(handler, msg)
            !handler.frontEnd && id == 2 -> handleLoginSuccess(handler, msg)
            !handler.frontEnd && id == 3 -> handleCompression(handler, msg)
            !handler.frontEnd && id == 4 -> forward(handler, msg) // Plugin request
            else -> throw IllegalArgumentException("Invalid packet ID")
        }
    }

    private fun forward(handler: CloudMinecraftHandler, msg: ByteBuf) {
        msg.resetReaderIndex()
        handler.other!!.write(msg.retain())
    }

    private fun handleLoginSuccess(handler: CloudMinecraftHandler, msg: ByteBuf) {
        handler.data!!.state = PlayState()
        forward(handler, msg)
    }

    private fun handleCompression(handler: CloudMinecraftHandler, msg: ByteBuf) {
        val pipe = handler.user.channel!!.pipeline()
        val threshold = Type.VAR_INT.readPrimitive(msg)

        val backPipe = pipe.get(CloudMinecraftHandler::class.java).other!!.pipeline()
        backPipe.get(CloudCompressor::class.java)?.threshold = threshold
        backPipe.get(CloudDecompressor::class.java)?.threshold = threshold

        forward(handler, msg)

        pipe.get(CloudCompressor::class.java).threshold = threshold
        pipe.get(CloudDecompressor::class.java).threshold = threshold
    }

    fun handleCryptoRequest(handler: CloudMinecraftHandler, msg: ByteBuf) {
        val data = handler.data!!
        data.backServerId = Type.STRING.read(msg)
        data.backPublicKey = KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(Type.BYTE_ARRAY_PRIMITIVE.read(msg)))
        data.backToken = Type.BYTE_ARRAY_PRIMITIVE.read(msg)

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

        val backMsg = ByteBufAllocator.DEFAULT.buffer()
        try {
            backMsg.writeByte(1) // Packet id
            Type.STRING.write(backMsg, id)
            Type.BYTE_ARRAY_PRIMITIVE.write(backMsg, mcCryptoKey.public.encoded)
            Type.BYTE_ARRAY_PRIMITIVE.write(backMsg, token)
            handler.other!!.write(backMsg.retain())
        } finally {
            backMsg.release()
        }
    }

    fun handleCryptoResponse(handler: CloudMinecraftHandler, msg: ByteBuf) {
        val frontHash = let {
            val frontKey = decryptRsa(mcCryptoKey.private, Type.BYTE_ARRAY_PRIMITIVE.read(msg))
            // RSA token - wat??? why is it encrypted with RSA if it was sent unencrypted?
            val decryptedToken = decryptRsa(mcCryptoKey.private, Type.BYTE_ARRAY_PRIMITIVE.read(msg))

            if (!decryptedToken.contentEquals(handler.data!!.frontToken!!)) throw IllegalStateException("invalid token!")

            val aesEn = mcCfb8(frontKey, Cipher.ENCRYPT_MODE)
            val aesDe = mcCfb8(frontKey, Cipher.DECRYPT_MODE)

            handler.user.channel!!.pipeline().get(CloudEncryptor::class.java).cipher = aesEn
            handler.user.channel!!.pipeline().get(CloudDecryptor::class.java).cipher = aesDe

            generateServerHash(handler.data!!.frontId!!, frontKey, mcCryptoKey.public)
        }

        val backKey = ByteArray(16).let {
            secureRandom.nextBytes(it)
            it
        }

        val backHash = generateServerHash(handler.data!!.backServerId!!, backKey, handler.data!!.backPublicKey!!)

        handler.user.channel!!.setAutoRead(false)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val profile = httpClient.get<JsonObject?>(
                        "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                                "${UrlEscapers.urlFormParameterEscaper().escape(handler.data!!.backName!!)}&serverId=$frontHash")
                        ?: throw IllegalArgumentException("Couldn't authenticate with session servers")

                val sessionJoin = viaWebServer.requestSessionJoin(
                        fromUndashed(profile.get("id")!!.asString),
                        handler.data!!.backName!!,
                        backHash,
                        handler.address!!, // Frontend handler
                        handler.data!!.backPublicKey!!
                )

                if (sessionJoin.first == 0) {
                    throw IllegalStateException("No browsers listening to this account, connect in /auth.html")
                } else {
                    sessionJoin.second.get(15, TimeUnit.SECONDS)
                    val backChan = handler.other!!
                    backChan.eventLoop().submit {
                        val backMsg = ByteBufAllocator.DEFAULT.buffer()
                        try {
                            backMsg.writeByte(1) // Packet id
                            Type.BYTE_ARRAY_PRIMITIVE.write(backMsg, encryptRsa(handler.data!!.backPublicKey!!, backKey))
                            Type.BYTE_ARRAY_PRIMITIVE.write(backMsg, encryptRsa(handler.data!!.backPublicKey!!, handler.data!!.backToken!!))
                            backChan.writeAndFlush(backMsg.retain())

                            val backAesEn = mcCfb8(backKey, Cipher.ENCRYPT_MODE)
                            val backAesDe = mcCfb8(backKey, Cipher.DECRYPT_MODE)

                            backChan.pipeline().get(CloudEncryptor::class.java).cipher = backAesEn
                            backChan.pipeline().get(CloudDecryptor::class.java).cipher = backAesDe
                        } finally {
                            backMsg.release()
                        }
                    }
                }
            } catch (e: Exception) {
                handler.disconnect("Online mode error: $e")
            }
            handler.user.channel!!.setAutoRead(true)
        }
    }

    fun handleLoginStart(handler: CloudMinecraftHandler, msg: ByteBuf) {
        handler.data!!.frontName = Type.STRING.read(msg)
        handler.data!!.backName = handler.user.get(CloudData::class.java)!!.altName ?: handler.data!!.frontName
        val backMsg = ByteBufAllocator.DEFAULT.buffer()
        try {
            msg.writeByte(0) // Id
            Type.STRING.write(msg, handler.data!!.backName)
            handler.other!!.write(msg.retain())
        } finally {
            backMsg.release()
        }
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        val packet = ByteBufAllocator.DEFAULT.buffer()
        try {
            handler.user.isPendingDisconnect = true

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

class StatusState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        handler.other!!.write(msg.retain())
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        handler.user.isPendingDisconnect = true

        val packet = ByteBufAllocator.DEFAULT.buffer()
        try {
            packet.writeByte(0) // id 0 disconnect
            Type.STRING.write(packet, """{"version": {"name": "VIAaaS", "protocol": -1}, "players":
| {"max": 0, "online": 0, "sample": []}, "description": {"text": ${Gson().toJson("§c$msg")}}}""".trimMargin())
            handler.user.sendRawPacketFuture(packet.retain())
                    .addListener { handler.user.channel?.close() }
        } finally {
            packet.release()
        }
    }
}

class PlayState : MinecraftConnectionState {
    override fun handleMessage(handler: CloudMinecraftHandler, ctx: ChannelHandlerContext, msg: ByteBuf) {
        handler.other!!.write(msg.retain())
    }

    override fun disconnect(handler: CloudMinecraftHandler, msg: String) {
        handler.user.disconnect(msg)
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