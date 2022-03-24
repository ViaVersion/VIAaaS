package com.viaversion.aas.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.MultimapBuilder
import com.google.common.collect.Multimaps
import com.google.common.net.HostAndPort
import com.google.gson.JsonObject
import com.viaversion.aas.*
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.util.StacklessException
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.netty.handler.codec.dns.DefaultDnsQuestion
import io.netty.handler.codec.dns.DnsPtrRecord
import io.netty.handler.codec.dns.DnsRecordType
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class WebServer {
    // I don't think i'll need more than 1k/day
    val clients = ConcurrentHashMap<WebSocketSession, WebClient>()
    val jwtAlgorithm = Algorithm.HMAC256(VIAaaSConfig.jwtSecret)

    fun generateToken(account: UUID, username: String): String {
        return JWT.create()
            .withIssuedAt(Date())
            .withNotBefore(Date())
            .withExpiresAt(Date.from(Instant.now().plus(Duration.ofDays(30))))
            .withSubject(account.toString())
            .withClaim("name", username)
            .withAudience("viaaas_listen")
            .withIssuer("viaaas")
            .sign(jwtAlgorithm)
    }

    data class UserInfo(val id: UUID, val name: String?)

    fun parseToken(token: String): UserInfo? {
        return try {
            val verified = JWT.require(jwtAlgorithm)
                .withAnyOfAudience("viaaas_listen")
                .build()
                .verify(token)
            UserInfo(UUID.fromString(verified.subject), verified.getClaim("name").asString())
        } catch (e: JWTVerificationException) {
            null
        }
    }

    // Minecraft account -> WebClient
    val listeners = Multimaps.synchronizedSetMultimap(
        MultimapBuilder.SetMultimapBuilder
            .hashKeys()
            .hashSetValues()
            .build<UUID, WebClient>()
    )
    val usernameIdCache = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build<String, CompletableFuture<UUID?>>(CacheLoader.from { name ->
            CoroutineScope(Dispatchers.IO).async {
                AspirinServer.httpClient
                    .get("https://api.mojang.com/users/profiles/minecraft/$name")
                    .body<JsonObject?>()?.get("id")?.asString?.let { parseUndashedId(it) }
            }.asCompletableFuture()
        })

    val sessionHashCallbacks = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build<String, CompletableFuture<Unit>>(CacheLoader.from { _ -> CompletableFuture() })
    val addressCallbacks = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build<UUID, CompletableFuture<AddressInfo>>(CacheLoader.from { _ -> CompletableFuture() })

    data class AddressInfo(val backVersion: Int, val backHostAndPort: HostAndPort, var frontOnline: Boolean? = null, var backName: String? = null)

    suspend fun requestAddressInfo(frontName: String): CompletableFuture<AddressInfo> {
        var onlineId: UUID? = null
        try {
            onlineId = usernameIdCache[frontName].await()
        } catch (e: java.lang.Exception) {
            webLogger.debug("Couldn't get online uuid for $frontName", e)
        }
        val offlineId = generateOfflinePlayerUuid(frontName)

        val callbackId = UUID.randomUUID()
        val future = addressCallbacks.get(callbackId)
        CoroutineScope(coroutineContext).apply {
            launch(Dispatchers.IO) {
                run sending@{
                    onlineId?.let {
                        if (sendRequestAddress(it, callbackId)) return@sending
                    }
                    if (sendRequestAddress(offlineId, callbackId)) return@sending
                    future.completeExceptionally(StacklessException("Username $frontName not listened. Use web auth to select backend server address."))
                }
            }
            launch {
                delay(20_000)
                future.completeExceptionally(StacklessException("No response from browser"))
            }
        }
        return future
    }

    private suspend fun sendRequestAddress(uuid: UUID, callbackId: UUID): Boolean {
        var sent = false
        listeners.get(uuid).forEach {
            it.ws.send(
                JsonObject().also {
                    it.addProperty("action", "parameters_request")
                    it.addProperty("callback", callbackId.toString())
                }.toString()
            )
            it.ws.flush()
            sent = true
        }
        return sent
    }

    suspend fun requestSessionJoin(
        frontName: String,
        id: UUID, name: String, hash: String,
        address: SocketAddress, backAddress: SocketAddress
    ): CompletableFuture<Unit> {
        val future = sessionHashCallbacks[hash]
        if (!listeners.containsKey(id)) {
            future.completeExceptionally(StacklessException("UUID $id ($frontName) isn't listened. Go to web auth."))
        } else {
            CoroutineScope(coroutineContext).apply {
                launch(Dispatchers.IO) {
                    var info: JsonObject? = null
                    var ptr: String? = null
                    if (address is InetSocketAddress) {
                        try {
                            val cleanedIp = address.hostString.substringBefore("%")
                            val ipLookup = async(Dispatchers.IO) {
                                AspirinServer.httpClient.get("https://ipinfo.io/$cleanedIp").body<JsonObject>()
                            }
                            val dnsQuery = AspirinServer.dnsResolver
                                .resolveAll(DefaultDnsQuestion(reverseLookup(address.address), DnsRecordType.PTR))
                            info = ipLookup.await()
                            ptr = dnsQuery.suspendAwait().let {
                                try {
                                    it.firstNotNullOfOrNull { it as? DnsPtrRecord }?.hostname()
                                        ?.removeSuffix(".")
                                } finally {
                                    it.forEach { ReferenceCountUtil.release(it) }
                                }
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                    val ipString =
                        "${info?.get("org")?.asString}, ${info?.get("country")?.asString}, ${info?.get("region")?.asString}, ${
                            info?.get("city")?.asString
                        }"
                    val msg = "Requester: $id $address ($ptr) ($ipString)\nBackend: $backAddress"
                    listeners[id].forEach {
                        it.ws.send(JsonObject().also {
                            it.addProperty("action", "session_hash_request")
                            it.addProperty("user", name)
                            it.addProperty("session_hash", hash)
                            it.addProperty("message", msg)
                        }.toString())
                        it.ws.flush()
                    }
                }
                launch {
                    delay(20_000)
                    future.completeExceptionally(StacklessException("No response from browser"))
                }
            }
        }
        return future
    }

    suspend fun connected(ws: WebSocketServerSession) {
        val loginState = WebLogin()
        val client = WebClient(this, ws, loginState)
        webLogger.info("+ WS: ${client.id}")
        clients[ws] = client
        loginState.start(client)
    }

    suspend fun onMessage(ws: WebSocketServerSession, msg: String) {
        val client = clients[ws]!!
        while (!client.rateLimiter.tryAcquire()) {
            delay(10)
        }
        client.state.onMessage(client, msg)
    }

    suspend fun disconnected(ws: WebSocketServerSession) {
        val client = clients[ws]!!
        webLogger.info("- WS: ${client.id}")
        client.state.disconnected(client)
        clients.remove(ws)
    }

    suspend fun onException(ws: WebSocketServerSession, exception: Exception) {
        val client = clients[ws]!!
        webLogger.info("WS Error: ${client.id} $exception")
        webLogger.debug("Ws exception: ", exception)
        client.state.onException(client, exception)
    }
}
