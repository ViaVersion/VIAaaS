package com.viaversion.aas.handler.state

import com.google.common.net.HostAndPort
import com.google.gson.JsonPrimitive
import com.viaversion.aas.*
import com.viaversion.aas.codec.CryptoCodec
import com.viaversion.aas.codec.packet.Packet
import com.viaversion.aas.codec.packet.login.*
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.aas.handler.forward
import com.viaversion.aas.handler.setCompression
import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.packet.State
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.api.type.Type
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import java.security.KeyPair
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom

class LoginState : ConnectionState {
    val callbackPlayerId = CompletableFuture<UUID>()
    lateinit var frontToken: ByteArray
    lateinit var frontServerId: String
    var frontOnline: Boolean? = null
    lateinit var frontName: String
    var backAddress: HostAndPort? = null
    lateinit var cryptoKey: KeyPair
    var extraData: String? = null
    var backName: String? = null
    var started = false
    override val state: State
        get() = State.LOGIN
    override val logDcInfo: Boolean
        get() = true
    var callbackPluginReauth = CompletableFuture<Boolean>()
    var pendingReauth: Int? = null

    override fun handlePacket(handler: MinecraftHandler, ctx: ChannelHandlerContext, packet: Packet) {
        when (packet) {
            is LoginStart -> handleLoginStart(handler, packet)
            is CryptoResponse -> handleCryptoResponse(handler, packet)
            is PluginResponse -> handlePluginResponse(handler, packet)
            is LoginDisconnect -> forward(handler, packet)
            is CryptoRequest -> handleCryptoRequest(handler, packet)
            is LoginSuccess -> handleLoginSuccess(handler, packet)
            is SetCompression -> handleCompression(handler, packet)
            is PluginRequest -> forward(handler, packet)
            else -> throw StacklessException("Invalid packet!")
        }
    }

    private fun handlePluginResponse(handler: MinecraftHandler, packet: PluginResponse) {
        if (packet.id == pendingReauth) {
            callbackPluginReauth.complete(packet.success)
            pendingReauth = null

            return
        }
        forward(handler, packet)
    }

    private fun handleLoginSuccess(handler: MinecraftHandler, loginSuccess: LoginSuccess) {
        handler.data.state = PlayState
        forward(handler, loginSuccess)
    }

    private fun handleCompression(handler: MinecraftHandler, setCompression: SetCompression) {
        val threshold = setCompression.threshold

        setCompression(handler.data.backChannel!!, threshold)

        forward(handler, setCompression)

        setCompression(handler.data.frontChannel, threshold)
    }

    fun authenticateOnlineFront(frontChannel: Channel) {
        // We'll use non-vanilla server id, public key size and token size
        frontToken = generate128Bits()
        frontServerId = generateServerId()

        val cryptoRequest = CryptoRequest()
        cryptoRequest.serverId = frontServerId
        cryptoKey = AspirinServer.mcCryptoKey
        cryptoRequest.publicKey = cryptoKey.public
        cryptoRequest.token = frontToken

        send(frontChannel, cryptoRequest, true)
    }

    fun reauthMessage(handler: MinecraftHandler, backName: String, backHash: String): CompletableFuture<Boolean> {
        if (!handler.data.frontEncrypted
            || !frontName.equals(backName, ignoreCase = true)
        ) {
            callbackPluginReauth.complete(false)
            return callbackPluginReauth
        }

        if (handler.data.frontVer!! < ProtocolVersion.v1_13.version) {
            encodeOpenAuth(backHash).forEach { data ->
                send(handler.data.frontChannel, SetCompression().also { it.threshold = data })
            }
            send(
                handler.data.frontChannel,
                SetCompression().also { it.threshold = handler.data.compressionLevel },
                flush = true
            )

            handler.coroutineScope.launch {
                delay(5000)
                callbackPluginReauth.complete(false)
            }
        } else {
            val buf = ByteBufAllocator.DEFAULT.buffer()
            try {
                Type.STRING.write(buf, backHash)

                val packet = PluginRequest()
                packet.id = ThreadLocalRandom.current().nextInt()
                packet.channel = "openauthmod:join"
                packet.data = readRemainingBytes(buf)
                send(handler.data.frontChannel, packet, true)
                pendingReauth = packet.id
            } finally {
                buf.release()
            }
        }
        return callbackPluginReauth
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
                val pluginReauthed = reauthMessage(handler, backName!!, backHash).await()
                if (!pluginReauthed) {
                    AspirinServer.viaWebServer.requestSessionJoin(
                        frontName,
                        playerId,
                        backName!!,
                        backHash,
                        frontHandler.endRemoteAddress,
                        handler.data.backHandler!!.endRemoteAddress
                    ).await()
                }

                val cryptoResponse = CryptoResponse()
                cryptoResponse.encryptedKey = encryptRsa(backPublicKey, backKey)
                cryptoResponse.encryptedToken = encryptRsa(backPublicKey, backToken)

                forward(frontHandler, cryptoResponse, true)
                backChan.pipeline().addBefore("frame", "crypto", CryptoCodec(aesKey(backKey), aesKey(backKey)))
            } catch (e: Exception) {
                frontHandler.data.frontChannel.fireExceptionCaughtIfOpen(e)
            }
        }
    }

    fun handleCryptoResponse(handler: MinecraftHandler, cryptoResponse: CryptoResponse) {
        val frontHash = let {
            val frontKey = decryptRsa(cryptoKey.private, cryptoResponse.encryptedKey)
            val decryptedToken = decryptRsa(cryptoKey.private, cryptoResponse.encryptedToken)

            if (!decryptedToken.contentEquals(frontToken)) throw StacklessException("Invalid verification token!")

            handler.data.frontChannel.pipeline()
                .addBefore("frame", "crypto", CryptoCodec(aesKey(frontKey), aesKey(frontKey)))

            generateServerHash(frontServerId, frontKey, cryptoKey.public)
        }

        handler.data.frontChannel.setAutoRead(false)
        handler.coroutineScope.launch(Dispatchers.IO) {
            try {
                val profile = hasJoined(frontName, frontHash)
                val id = profile["id"]!!.asString

                callbackPlayerId.complete(parseUndashedId(id))
            } catch (e: Exception) {
                callbackPlayerId.completeExceptionally(e)
            }
            handler.data.frontChannel.setAutoRead(true)
        }
    }

    fun handleLoginStart(handler: MinecraftHandler, loginStart: LoginStart) {
        if (started) {
            if (loginStart.username.startsWith(OPENAUTH_MAGIC_PREFIX)) {
                callbackPluginReauth.complete(loginStart.username.removePrefix(OPENAUTH_MAGIC_PREFIX).toBoolean())
                return
            }
            throw StacklessException("Login already started")
        }
        started = true

        VIAaaSConfig.maxPlayers?.let {
            if (AspirinServer.currentPlayers() >= it) throw StacklessException("Instance is full!")
        }

        frontName = loginStart.username
        backName = backName ?: frontName

        handler.coroutineScope.launch(Dispatchers.IO) {
            try {
                if (backAddress == null) {
                    mcLogger.info("Requesting address info from web for $frontName")
                    val info = AspirinServer.viaWebServer.requestAddressInfo(frontName).await()
                    backAddress = info.backHostAndPort
                    handler.data.backServerVer = info.backVersion
                    frontOnline = info.frontOnline
                }
                if (VIAaaSConfig.forceOnlineMode) frontOnline = true
                if (frontOnline != null) {
                    when (frontOnline) {
                        false -> callbackPlayerId.complete(generateOfflinePlayerUuid(frontName))
                        true -> authenticateOnlineFront(handler.data.frontChannel) // forced
                        else -> {}
                    }
                    val id = callbackPlayerId.await()
                    mcLogger.info("Login: ${handler.endRemoteAddress} $frontName $id")
                }
                connectBack(handler, HostAndPort.fromParts(backAddress!!.host, backAddress!!.port), State.LOGIN, extraData)
                loginStart.username = backName!!
                send(handler.data.backChannel!!, loginStart, true)
            } catch (e: Exception) {
                handler.data.frontChannel.fireExceptionCaughtIfOpen(e)
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
