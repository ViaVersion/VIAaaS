package com.github.creeper123123321.viaaas

import com.google.common.base.Preconditions
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import java.net.URLEncoder
import java.time.Duration
import java.util.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.collections.set


// todo https://minecraft.id/documentation

val viaWebServer = WebDashboardServer()

class ViaWebApp {
    fun Application.main() {
        install(DefaultHeaders)
        install(CallLogging)
        install(WebSockets) {
            pingPeriod = Duration.ofMinutes(1)
        }

        routing {
            webSocket("/ws") {
                try {
                    viaWebServer.connected(this)
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            viaWebServer.onMessage(this, frame.readText())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    viaWebServer.onException(this, e)
                    this.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.toString()))
                } finally {
                    viaWebServer.disconnected(this)
                }
            }

            static {
                defaultResource("index.html", "web")
                resources("web")
            }
        }
    }
}

// https://github.com/VelocityPowered/Velocity/blob/6467335f74a7d1617512a55cc9acef5e109b51ac/api/src/main/java/com/velocitypowered/api/util/UuidUtils.java
fun fromUndashed(string: String): UUID {
    Preconditions.checkArgument(string.length == 32, "Length is incorrect")
    return UUID(
            java.lang.Long.parseUnsignedLong(string.substring(0, 16), 16),
            java.lang.Long.parseUnsignedLong(string.substring(16), 16)
    )
}

class WebDashboardServer {
    val clients = ConcurrentHashMap<WebSocketSession, WebClient>()
    val loginTokens = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.DAYS)
            .build<UUID, UUID>()

    // Minecraft account -> WebClient
    val listeners = ConcurrentHashMap<UUID, MutableSet<WebClient>>()
    val usernameIdCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build<String, UUID>(CacheLoader.from { name ->
                runBlocking {
                    httpClient.get<JsonObject?>("https://api.mojang.com/users/profiles/minecraft/$name")
                            ?.get("id")?.asString?.let { fromUndashed(it) }
                }
            })

    val pendingSessionHashes = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build<String, CompletableFuture<Void>>(CacheLoader.from { _ -> CompletableFuture() })

    suspend fun connected(ws: WebSocketSession) {
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


data class WebClient(val server: WebDashboardServer,
                     val ws: WebSocketSession,
                     val state: WebState,
                     val listenedIds: MutableSet<UUID> = mutableSetOf())

interface WebState {
    suspend fun start(webClient: WebClient)
    suspend fun onMessage(webClient: WebClient, msg: String)
    suspend fun disconnected(webClient: WebClient)
    suspend fun onException(webClient: WebClient, exception: java.lang.Exception)
}

class WebLogin : WebState {
    override suspend fun start(webClient: WebClient) {
        webClient.ws.send("""{"action": "ad_minecraft_id_login"}""")
        webClient.ws.flush()
    }

    override suspend fun onMessage(webClient: WebClient, msg: String) {
        val obj = Gson().fromJson<JsonObject>(msg, JsonObject::class.java)

        when (obj.getAsJsonPrimitive("action").asString) {
            "minecraft_id_login" -> {
                val username = obj.getAsJsonPrimitive("username").asString
                val code = obj.getAsJsonPrimitive("code").asString

                val check = httpClient.submitForm<JsonObject>(
                        "https://api.minecraft.id/gateway/verify/${URLEncoder.encode(username, Charsets.UTF_8)}",
                        formParameters = parametersOf("code", code),
                        encodeInQuery = false) {
                }


                if (check.getAsJsonPrimitive("valid").asBoolean) {
                    val token = UUID.randomUUID()
                    val mcIdUser = check.get("username").asString
                    val uuid = webClient.server.usernameIdCache.get(mcIdUser)

                    webClient.server.loginTokens.put(token, uuid)
                    webClient.ws.send("""{"action": "minecraft_id_result", "success": true,
                        | "username": "$mcIdUser", "uuid": "$uuid", "token": "$token"}""".trimMargin())
                } else {
                    webClient.ws.send("""{"action": "minecraft_id_result", "success": false}""")
                }
            }
            "listen_login_requests" -> {
                val token = UUID.fromString(obj.getAsJsonPrimitive("token").asString)
                val user = webClient.server.loginTokens.getIfPresent(token)
                if (user != null) {
                    webClient.ws.send("""{"action": "listen_login_requests_result", "token": "$token", "success": true, "username": "$user"}""")
                    webClient.listenedIds.add(user)
                    webClient.server.listeners.computeIfAbsent(user) { Collections.newSetFromMap(ConcurrentHashMap()) }
                            .add(webClient)
                } else {
                    webClient.ws.send("""{"action": "listen_login_requests_result", "token": "$token", "success": false}""")
                }
            }
            "session_hash_response" -> {
                val hash = obj.get("session_hash").asString
                webClient.server.pendingSessionHashes.getIfPresent(hash)?.complete(null)
            }
            else -> throw IllegalStateException("invalid action!")
        }

        webClient.ws.flush()
    }

    override suspend fun disconnected(webClient: WebClient) {
        webClient.listenedIds.forEach { webClient.server.listeners[it]?.remove(webClient) }
    }

    override suspend fun onException(webClient: WebClient, exception: java.lang.Exception) {
    }
}