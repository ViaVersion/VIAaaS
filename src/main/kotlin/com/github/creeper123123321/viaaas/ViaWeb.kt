package com.github.creeper123123321.viaaas

import com.google.common.cache.CacheBuilder
import com.google.gson.Gson
import com.google.gson.JsonObject
import io.ktor.application.*
import io.ktor.client.request.forms.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import java.net.URLEncoder
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.collections.set

// todo https://minecraft.id/documentation

class ViaWebApp {
    val server = WebDashboardServer()

    fun Application.main() {
        install(DefaultHeaders)
        install(CallLogging)
        install(WebSockets) {
            pingPeriod = Duration.ofMinutes(1)
        }

        routing {
            webSocket("/ws") {
                try {
                    server.connected(this)
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            server.onMessage(this, frame.readText())
                        }
                    }
                } catch (e: Exception) {
                    server.onException(this, e)
                    this.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.toString()))
                } finally {
                    server.disconnected(this)
                }
            }

            static {
                defaultResource("index.html", "web")
                resources("web")
            }
        }
    }
}

class WebDashboardServer {
    val clients = ConcurrentHashMap<WebSocketSession, WebClient>()
    val loginTokens = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.DAYS)
            .build<UUID, String>()
    val usernames = ConcurrentHashMap<String, WebClient>()

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
                     val listenedUsernames: MutableSet<String> = mutableSetOf())

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
                    webClient.server.loginTokens.put(token, username)
                    webClient.ws.send("""{"action": "minecraft_id_result", "success": true,
                        | "username": "$username", "token": "$token"}""".trimMargin())
                } else {
                    webClient.ws.send("""{"action": "minecraft_id_result", "success": false}""")
                }
            }
            "listen_login_requests" -> {
                val token = UUID.fromString(obj.getAsJsonPrimitive("token").asString)
                val user = webClient.server.loginTokens.get(token) { "" }
                if (user != "") {
                    webClient.ws.send("""{"action": "listen_login_requests_result", "token": "$token", "success": true, "username": "$user"}""")
                    webClient.listenedUsernames.add(user)
                    webClient.server.usernames[user] = webClient
                } else {
                    webClient.ws.send("""{"action": "listen_login_requests_result", "token": "$token", "success": false}""")
                }
            }
            "session_hash_response" -> {
                val token = UUID.fromString(obj.getAsJsonPrimitive("token").asString)
                val user = webClient.server.loginTokens.get(token) { null }!!
            }
            else -> throw IllegalStateException("invalid action!")
        }

        webClient.ws.flush()
    }

    override suspend fun disconnected(webClient: WebClient) {
        webClient.listenedUsernames.forEach { webClient.server.usernames.remove(it, webClient) }
    }

    override suspend fun onException(webClient: WebClient, exception: java.lang.Exception) {
    }
}