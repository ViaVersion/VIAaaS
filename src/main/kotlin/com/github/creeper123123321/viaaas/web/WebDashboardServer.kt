package com.github.creeper123123321.viaaas.web

import com.github.creeper123123321.viaaas.httpClient
import com.github.creeper123123321.viaaas.parseUndashedId
import com.github.creeper123123321.viaaas.viaWebServer
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.gson.JsonObject
import io.ktor.client.request.*
import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import us.myles.ViaVersion.api.Via
import java.net.SocketAddress
import java.security.PublicKey
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class WebDashboardServer {
    val clients = ConcurrentHashMap<WebSocketSession, WebClient>()
    val loginTokens = CacheBuilder.newBuilder()
        .expireAfterAccess(10, TimeUnit.DAYS)
        .build<UUID, UUID>()

    // Minecraft account -> WebClient
    val listeners = ConcurrentHashMap<UUID, MutableSet<WebClient>>()
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
        address: SocketAddress, backKey: PublicKey
    )
            : CompletableFuture<Unit> {
        val future = viaWebServer.pendingSessionHashes.get(hash)
        var sent = 0
        viaWebServer.listeners[id]?.forEach {
            it.ws.send(
                """{"action": "session_hash_request", "user": "$name", "session_hash": "$hash",
                                        | "client_address": "$address", "backend_public_key":
                                        | "${Base64.getEncoder().encodeToString(backKey.encoded)}"}""".trimMargin()
            )
            it.ws.flush()
            sent++
        }
        if (sent != 0) {
            Via.getPlatform().runSync({
                future.completeExceptionally(TimeoutException("No response from browser"))
            }, 15 * 20)
        } else {
            future.completeExceptionally(IllegalStateException("No browser listening"))
        }
        return future
    }

    suspend fun connected(ws: WebSocketServerSession) {
        val loginState = WebLogin()
        val client = WebClient(this, ws, loginState)
        clients[ws] = client
        loginState.start(client)
    }

    suspend fun onMessage(ws: WebSocketSession, msg: String) {
        val client = clients[ws]!!
        client.state.onMessage(client, msg)
    }

    suspend fun disconnected(ws: WebSocketSession) {
        val client = clients[ws]!!
        client.state.disconnected(client)
        clients.remove(ws)
    }

    suspend fun onException(ws: WebSocketSession, exception: java.lang.Exception) {
        val client = clients[ws]!!
        client.state.onException(client, exception)
    }
}