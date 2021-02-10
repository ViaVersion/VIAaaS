package com.github.creeper123123321.viaaas.handler.state

import com.github.creeper123123321.viaaas.*
import com.github.creeper123123321.viaaas.codec.CompressionCodec
import com.github.creeper123123321.viaaas.codec.CryptoCodec
import com.github.creeper123123321.viaaas.packet.*
import com.github.creeper123123321.viaaas.packet.login.*
import com.github.creeper123123321.viaaas.handler.CloudMinecraftHandler
import com.github.creeper123123321.viaaas.handler.forward
import com.google.common.net.UrlEscapers
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.client.request.*
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import us.myles.ViaVersion.packets.State
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.crypto.Cipher

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
            backPipe.addAfter("frame", "compress", CompressionCodec(threshold))
        } else if (backPipe.get("compress") != null) {
            backPipe.remove("compress")
        }

        forward(handler, setCompression)

        if (threshold != -1) {
            pipe.addAfter("frame", "compress", CompressionCodec(threshold))
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

        send(frontHandler.data.frontChannel, cryptoRequest, true)
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
                            backChan.pipeline().addBefore("frame", "crypto", CryptoCodec(backAesDe, backAesEn))
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

            handler.data.frontChannel.pipeline().addBefore("frame", "crypto", CryptoCodec(aesDe, aesEn))

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
        if (handler.data.frontName != null) throw IllegalStateException("Login already started")

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
        writeFlushClose(handler.data.frontChannel, packet)
    }
}