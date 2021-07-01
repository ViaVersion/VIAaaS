package com.viaversion.aas.handler.state

import com.google.common.net.HostAndPort
import com.google.gson.JsonPrimitive
import com.viaversion.aas.*
import com.viaversion.aas.codec.CompressionCodec
import com.viaversion.aas.codec.CryptoCodec
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.login.*
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.forward
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.crypto.Cipher

class LoginState : MinecraftConnectionState {
    val callbackPlayerId = CompletableFuture<UUID>()
    lateinit var frontToken: ByteArray
    lateinit var frontServerId: String
    var frontOnline: Boolean? = null
    lateinit var frontName: String
    lateinit var backAddress: HostAndPort
    var extraData: String? = null
    var backName: String? = null
    var started = false
    override val state: State
        get() = State.LOGIN
    override val logDc: Boolean
        get() = true

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

        if (backPipe.get("compress") != null) {
            backPipe.remove("compress")
        }
        if (threshold != -1) {
            backPipe.addAfter("frame", "compress", CompressionCodec(threshold))
        }

        forward(handler, setCompression)

        if (pipe.get("compress") != null) {
            pipe.remove("compress")
        }
        if (threshold != -1) {
            pipe.addAfter("frame", "compress", CompressionCodec(threshold))
        }
    }

    fun authenticateOnlineFront(frontChannel: Channel) {
        // We'll use non-vanilla server id, public key size and token size
        frontToken = generate128Bits()
        frontServerId = generateServerId()

        val cryptoRequest = CryptoRequest()
        cryptoRequest.serverId = frontServerId
        cryptoRequest.publicKey = AspirinServer.mcCryptoKey.public
        cryptoRequest.token = frontToken

        send(frontChannel, cryptoRequest, true)
    }

    fun handleCryptoRequest(handler: MinecraftHandler, cryptoRequest: CryptoRequest) {
        val backServerId = cryptoRequest.serverId
        val backPublicKey = cryptoRequest.publicKey
        val backToken = cryptoRequest.token

        if (!callbackPlayerId.isDone) {
            authenticateOnlineFront(handler.data.frontChannel)
        }
        val frontHandler = handler.data.frontHandler
        val backChan = handler.data.backChannel!!

        handler.coroutineScope.launch {
            try {
                val playerId = callbackPlayerId.await()

                val backKey = generate128Bits()
                val backHash = generateServerHash(backServerId, backKey, backPublicKey)

                mcLogger.info("Session req: ${handler.data.frontHandler.endRemoteAddress} ($playerId $frontName) $backName")
                AspirinServer.viaWebServer.requestSessionJoin(
                    playerId,
                    backName!!,
                    backHash,
                    frontHandler.endRemoteAddress,
                    handler.data.backHandler!!.endRemoteAddress
                ).await()

                val cryptoResponse = CryptoResponse()
                cryptoResponse.encryptedKey = encryptRsa(backPublicKey, backKey)
                cryptoResponse.encryptedToken = encryptRsa(backPublicKey, backToken)
                val backAesEn = mcCfb8(backKey, Cipher.ENCRYPT_MODE)
                val backAesDe = mcCfb8(backKey, Cipher.DECRYPT_MODE)

                forward(frontHandler, cryptoResponse, true)
                backChan.pipeline().addBefore("frame", "crypto", CryptoCodec(backAesEn, backAesDe))
            } catch (e: Exception) {
                frontHandler.data.frontChannel.pipeline().fireExceptionCaught(e)
            }
        }
    }

    fun handleCryptoResponse(handler: MinecraftHandler, cryptoResponse: CryptoResponse) {
        val frontHash = let {
            val frontKey = decryptRsa(AspirinServer.mcCryptoKey.private, cryptoResponse.encryptedKey)
            val decryptedToken = decryptRsa(AspirinServer.mcCryptoKey.private, cryptoResponse.encryptedToken)

            if (!decryptedToken.contentEquals(frontToken)) throw StacklessException("Invalid verification token!")

            val aesEn = mcCfb8(frontKey, Cipher.ENCRYPT_MODE)
            val aesDe = mcCfb8(frontKey, Cipher.DECRYPT_MODE)
            handler.data.frontChannel.pipeline().addBefore("frame", "crypto", CryptoCodec(aesEn, aesDe))

            generateServerHash(frontServerId, frontKey, AspirinServer.mcCryptoKey.public)
        }

        handler.data.frontChannel.setAutoRead(false)
        handler.coroutineScope.launch(Dispatchers.IO) {
            try {
                val profile = hasJoined(frontName, frontHash)
                val id = profile.get("id")!!.asString

                callbackPlayerId.complete(parseUndashedId(id))
            } catch (e: Exception) {
                callbackPlayerId.completeExceptionally(e)
            }
            handler.data.frontChannel.setAutoRead(true)
        }
    }

    fun handleLoginStart(handler: MinecraftHandler, loginStart: LoginStart) {
        if (started) throw StacklessException("Login already started")
        started = true

        VIAaaSConfig.maxPlayers?.let {
            if (currentPlayers() >= it) throw StacklessException("Instance is full!")
        }

        frontName = loginStart.username
        backName = backName ?: frontName

        handler.data.frontChannel.setAutoRead(false)
        handler.coroutineScope.launch(Dispatchers.IO) {
            try {
                if (frontOnline != null) {
                    when (frontOnline) {
                        false -> callbackPlayerId.complete(generateOfflinePlayerUuid(frontName))
                        true -> authenticateOnlineFront(handler.data.frontChannel) // forced
                    }
                    val id = callbackPlayerId.await()
                    mcLogger.info("Login: ${handler.endRemoteAddress} $frontName $id")
                }
                connectBack(handler, backAddress.host, backAddress.port, State.LOGIN, extraData)
                loginStart.username = backName!!
                send(handler.data.backChannel!!, loginStart, true)
            } catch (e: Exception) {
                handler.data.frontChannel.pipeline().fireExceptionCaught(e)
            }
        }
    }

    override fun disconnect(handler: MinecraftHandler, msg: String) {
        super.disconnect(handler, msg)

        val packet = LoginDisconnect()
        packet.msg = JsonPrimitive("[VIAaaS] §c$msg").toString()
        writeFlushClose(handler.data.frontChannel, packet)
    }
}
