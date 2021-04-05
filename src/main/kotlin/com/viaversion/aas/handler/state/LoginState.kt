package com.viaversion.aas.handler.state

import com.viaversion.aas.*
import com.viaversion.aas.codec.CompressionCodec
import com.viaversion.aas.codec.CryptoCodec
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.forward
import com.viaversion.aas.packet.*
import com.viaversion.aas.packet.login.*
import com.google.gson.Gson
import com.viaversion.aas.util.StacklessException
import io.ktor.client.request.*
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import us.myles.ViaVersion.packets.State
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.crypto.Cipher
import io.netty.channel.Channel

class LoginState : MinecraftConnectionState {
    val callbackPlayerId = CompletableFuture<String>()
    lateinit var frontToken: ByteArray
    lateinit var frontServerId: String
    var frontOnline: Boolean? = null
    lateinit var frontName: String
    lateinit var backAddress: Pair<String, Int>
    var backName: String? = null
    var started = false
    override val state: State
        get() = State.LOGIN

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        when (packet) {
            is LoginStart -> handleLoginStart(handler, packet)
            is CryptoResponse -> handleCryptoResponse(handler, packet)
            is PluginResponse -> forward(handler, packet)
            is LoginDisconnect -> forward(handler, packet)
            is CryptoRequest -> handleCryptoRequest(handler, packet)
            is LoginSuccess -> handleLoginSuccess(handler, packet)
            is SetCompression -> handleCompression(handler, packet)
            is PluginRequest -> forward(handler, packet)
            else -> throw StacklessException("Invalid packet!")
        }
    }

    private fun handleLoginSuccess(handler: MinecraftHandler, loginSuccess: LoginSuccess) {
        handler.data.state = PlayState
        forward(handler, loginSuccess)
    }

    private fun handleCompression(handler: MinecraftHandler, setCompression: SetCompression) {
        val pipe = handler.data.frontChannel.pipeline()
        val threshold = setCompression.threshold

        val backPipe = pipe.get(MinecraftHandler::class.java).other!!.pipeline()
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

    fun authenticateOnlineFront(frontChannel: Channel) {
        // We'll use non-vanilla server id, public key size and token size
        frontToken = generate128Bits()
        frontServerId = generateServerId()

        val cryptoRequest = CryptoRequest()
        cryptoRequest.serverId = frontServerId
        cryptoRequest.publicKey = mcCryptoKey.public
        cryptoRequest.token = frontToken

        send(frontChannel, cryptoRequest, true)
    }

    fun handleCryptoRequest(handler: MinecraftHandler, cryptoRequest: CryptoRequest) {
        val backServerId = cryptoRequest.serverId
        val backPublicKey = cryptoRequest.publicKey
        val backToken = cryptoRequest.token

        if (frontOnline == null) {
            authenticateOnlineFront(handler.data.frontChannel)
        }

        callbackPlayerId.whenComplete { playerId, e ->
            if (e != null) return@whenComplete
            val frontHandler = handler.data.frontHandler
            val backChan = handler.data.backChannel!!

            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val backKey = generate128Bits()
                    val backHash = generateServerHash(backServerId, backKey, backPublicKey)

                    viaWebServer.requestSessionJoin(
                        parseUndashedId(playerId),
                        backName!!,
                        backHash,
                        frontHandler.endRemoteAddress,
                        handler.data.backHandler!!.endRemoteAddress
                    ).whenCompleteAsync({ _, throwable ->
                        if (throwable != null) {
                            frontHandler.data.frontChannel.pipeline()
                                .fireExceptionCaught(throwable)
                            return@whenCompleteAsync
                        }

                        val cryptoResponse = CryptoResponse()
                        cryptoResponse.encryptedKey = encryptRsa(backPublicKey, backKey)
                        cryptoResponse.encryptedToken = encryptRsa(backPublicKey, backToken)
                        forward(frontHandler, cryptoResponse, true)

                        val backAesEn = mcCfb8(backKey, Cipher.ENCRYPT_MODE)
                        val backAesDe = mcCfb8(backKey, Cipher.DECRYPT_MODE)
                        backChan.pipeline().addBefore("frame", "crypto", CryptoCodec(backAesDe, backAesEn))
                    }, backChan.eventLoop())
                } catch (e: Exception) {
                    frontHandler.data.frontChannel.pipeline()
                        .fireExceptionCaught(e)
                }
            }
        }
    }

    fun handleCryptoResponse(handler: MinecraftHandler, cryptoResponse: CryptoResponse) {
        val frontHash = let {
            val frontKey = decryptRsa(mcCryptoKey.private, cryptoResponse.encryptedKey)
            val decryptedToken = decryptRsa(mcCryptoKey.private, cryptoResponse.encryptedToken)

            if (!decryptedToken.contentEquals(frontToken)) throw StacklessException("Invalid verification token!")

            val aesEn = mcCfb8(frontKey, Cipher.ENCRYPT_MODE)
            val aesDe = mcCfb8(frontKey, Cipher.DECRYPT_MODE)
            handler.data.frontChannel.pipeline().addBefore("frame", "crypto", CryptoCodec(aesDe, aesEn))

            generateServerHash(frontServerId, frontKey, mcCryptoKey.public)
        }

        handler.data.frontChannel.setAutoRead(false)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val profile = hasJoined(frontName, frontHash)
                val id = profile.get("id")!!.asString

                callbackPlayerId.complete(id)
            } catch (e: Exception) {
                callbackPlayerId.completeExceptionally(e)
            }
            handler.data.frontChannel.setAutoRead(true)
        }
    }

    fun handleLoginStart(handler: MinecraftHandler, loginStart: LoginStart) {
        if (loginStart.username.length > 16) throw badLength
        if (started) throw StacklessException("Login already started")
        started = true

        frontName = loginStart.username
        backName = backName ?: frontName

        val connect = {
            connectBack(handler, backAddress.first, backAddress.second, State.LOGIN) {
                loginStart.username = backName!!
                send(handler.data.backChannel!!, loginStart, true)
            }
        }

        callbackPlayerId.whenComplete { id, e ->
            if (e != null) {
                disconnect(handler, "Profile error: $e")
            } else {
                mcLogger.info("Login: ${handler.endRemoteAddress} $frontName $id")
                if (frontOnline != null) {
                    connect()
                }
            }
        }

        when (frontOnline) {
            false -> callbackPlayerId.complete(generateOfflinePlayerUuid(frontName).toString().replace("-", ""))
            true -> authenticateOnlineFront(handler.data.frontChannel) // forced
            null -> connect() // Connect then authenticate
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = LoginDisconnect()
        packet.msg = Gson().toJson("[VIAaaS] §c$msg")
        writeFlushClose(handler.data.frontChannel, packet)
    }
}
