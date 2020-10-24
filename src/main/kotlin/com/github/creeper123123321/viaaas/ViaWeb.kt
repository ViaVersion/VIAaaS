package com.github.creeper123123321.viaaas

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

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
                server.connected(this)
                try {
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            server.onMessage(this, frame.readText())
                        }
                    }
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
    suspend fun connected(ws: WebSocketSession) {
        val loginState = WebLogin()
        val client = WebClient(ws, loginState)
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
}


data class WebClient(val ws: WebSocketSession, val state: WebState) {
}

interface WebState {
    suspend fun start(webClient: WebClient)
    suspend fun onMessage(webClient: WebClient, msg: String)
    suspend fun disconnected(webClient: WebClient)
}

class WebLogin : WebState {
    override suspend fun start(webClient: WebClient) {
        webClient.ws.send("test")
        webClient.ws.flush()
    }

    override suspend fun onMessage(webClient: WebClient, msg: String) {
        TODO("Not yet implemented")
    }

    override suspend fun disconnected(webClient: WebClient) {
        TODO("Not yet implemented")
    }
}