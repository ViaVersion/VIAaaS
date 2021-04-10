package com.viaversion.aas.web

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.MultimapBuilder
import com.google.common.collect.Multimaps
import com.google.gson.JsonObject
import com.viaversion.aas.config.VIAaaSConfig
import com.viaversion.aas.httpClient
import com.viaversion.aas.parseUndashedId
import com.viaversion.aas.util.StacklessException
import com.viaversion.aas.webLogger
import io.ipinfo.api.IPInfo
import io.ktor.client.request.*
import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.time.delay
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class WebDashboardServer {
    // I don't think i'll need more than 1k/day
    val ipInfo = IPInfo.builder().setToken("").build()
    val clients = ConcurrentHashMap<WebSocketSession, WebClient>()
    val jwtAlgorithm = Algorithm.HMAC256(VIAaaSConfig.jwtSecret)

    fun generateToken(account: UUID): String {
        return JWT.create()
            .withExpiresAt(Date.from(Instant.now().plus(Duration.ofDays(30))))
            .withSubject("mc_account")
            .withClaim("can_listen", true)
            .withClaim("mc_uuid", account.toString())
            .withIssuer("viaaas")
            .sign(jwtAlgorithm)
    }

    fun checkToken(token: String): UUID? {
        try {
            val verified = JWT.require(jwtAlgorithm)
                .withSubject("mc_account")
                .withClaim("can_listen", true)
                .withClaimPresence("mc_uuid")
                .build()
                .verify(token)

            return UUID.fromString(verified.getClaim("mc_uuid").asString())
        } catch (e: JWTVerificationException) {
            return null
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
        .build<String, UUID>(CacheLoader.from { name ->
            runBlocking {
                withContext(Dispatchers.IO) {
                    httpClient.get<JsonObject?>("https://api.mojang.com/users/profiles/minecraft/$name")
                        ?.get("id")?.asString?.let { parseUndashedId(it) }
                }
            }
        })

    val sessionHashCallbacks = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build<String, CompletableFuture<Unit>>(CacheLoader.from { _ -> CompletableFuture() })

    suspend fun requestSessionJoin(
        id: UUID, name: String, hash: String,
        address: SocketAddress, backAddress: SocketAddress
    ): CompletableFuture<Unit> {
        val future = sessionHashCallbacks.get(hash)
        if (!listeners.containsKey(id)) {
            future.completeExceptionally(StacklessException("No browser listening"))
        } else {
            val info = try {
                if (address is InetSocketAddress) {
                    ipInfo.lookupIP(address.address.hostAddress.substringBefore("%"))
                } else null
            } catch (ignored: Exception) {
                null
            }
            val msg =
                "Client: $address (${info?.org}, ${info?.city}, ${info?.region}, ${info?.countryCode})\nBackend: $backAddress"
            listeners[id]?.forEach {
                it.ws.send(
                    JsonObject().also {
                        it.addProperty("action", "session_hash_request")
                        it.addProperty("user", name)
                        it.addProperty("session_hash", hash)
                        it.addProperty("message", msg)
                    }.toString()
                )
                it.ws.flush()
            }
            GlobalScope.launch {
                delay(Duration.ofSeconds(20))
                future.completeExceptionally(StacklessException("No response from browser"))
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
