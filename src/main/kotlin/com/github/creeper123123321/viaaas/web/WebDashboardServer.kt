package com.github.creeper123123321.viaaas.web

import com.github.creeper123123321.viaaas.httpClient
import com.github.creeper123123321.viaaas.parseUndashedId
import com.github.creeper123123321.viaaas.webLogger
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimaps
import com.google.gson.JsonObject
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import us.myles.ViaVersion.api.Via
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import io.ipinfo.api.IPInfo

class WebDashboardServer {
    // I don't think i'll need more than 1k/day
    val ipInfo = IPInfo.builder().setToken("").build()
    val clients = ConcurrentHashMap<WebSocketSession, WebClient>()
    val loginTokens = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.DAYS)
        .build<UUID, UUID>()

    fun generateToken(account: UUID): UUID {
        return UUID.randomUUID().also { loginTokens.put(it, account) }
    }

    // Minecraft account -> WebClient
    val listeners = Multimaps.synchronizedListMultimap(ArrayListMultimap.create<UUID, WebClient>())
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

    val pendingSessionHashes = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build<String, CompletableFuture<Unit>>(CacheLoader.from { _ -> CompletableFuture() })

    suspend fun requestSessionJoin(
        id: UUID, name: String, hash: String,
        address: SocketAddress, backAddress: SocketAddress
    ): CompletableFuture<Unit> {
        val future = pendingSessionHashes.get(hash)
        if (!listeners.containsKey(id)) {
            future.completeExceptionally(IllegalStateException("No browser listening"))
        } else {
            val info = try {
                if (address is InetSocketAddress) {
                    ipInfo.lookupIP(address.address.hostAddress.substringBefore("%"))
                } else null
            } catch (ignored: Exception) { null }
            val msg = "Client: $address (${info?.org}, ${info?.city}, ${info?.region}, ${info?.countryCode})\nBackend: $backAddress"
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
            Via.getPlatform().runSync({
                future.completeExceptionally(TimeoutException("No response from browser"))
            }, 15 * 20)
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
        client.rateLimiter.acquire()
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
