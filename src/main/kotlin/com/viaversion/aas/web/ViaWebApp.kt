package com.viaversion.aas.web

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.conditionalheaders.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.consumeEach
import org.slf4j.event.Level
import java.io.File
import java.nio.file.Path
import java.time.Duration

class ViaWebApp(val viaWebServer: WebServer) {
    fun Application.main() {
        install(DefaultHeaders)
        install(CallLogging) {
            level = Level.DEBUG
            this.format {
                "${it.request.local.method.value} ${it.response.status()?.value}" +
                        " ${it.request.local.remoteHost}:${it.request.local.remotePort}" +
                        " (O: ${it.request.origin.remoteHost}:${it.request.origin.remotePort}) " +
                        "${it.request.local.scheme}://${it.request.local.serverHost}:${it.request.local.serverPort}${it.request.local.uri}"
            }
        }
        install(WebSockets) {
            maxFrameSize = Short.MAX_VALUE.toLong()
            pingPeriod = Duration.ofSeconds(20)
            timeout = Duration.ofSeconds(15)
            contentConverter = GsonWebsocketContentConverter()
        }
        install(XForwardedHeaders)
        install(ForwardedHeaders)
        install(ContentNegotiation) {
            gson()
        }
        routing {
            routeRoot()
            routeWs()
            routeApi()
        }
    }

    private fun Route.routeRoot() {
        route("/") {
            // https://ktor.io/docs/compression.html#security
            install(Compression)
            install(CachingHeaders) {
                options { _, _ ->
                    CachingOptions(CacheControl.MaxAge(600, visibility = CacheControl.Visibility.Public))
                }
            }
            install(ConditionalHeaders)
            install(PartialContent)
            get("{path...}") {
                val relativePath = Path.of(call.parameters.getAll("path")?.joinToString("/") ?: "")
                val index = Path.of("index.html")

                val resource = call.resolveResource(relativePath.toString(), "web")
                    ?: call.resolveResource(relativePath.resolve(index).toString(), "web")

                var file = File("config/web/").combineSafe(relativePath)
                if (file.isDirectory) {
                    file = file.resolve(index.toFile())
                }

                when {
                    file.isFile -> call.respondFile(file)
                    resource != null -> call.respond(resource)
                }
            }
        }
    }

    private fun Route.routeWs() {
        webSocket("/ws") {
            try {
                viaWebServer.connected(this)
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        viaWebServer.onMessage(this, frame.readText())
                    }
                }
            } catch (ignored: ClosedReceiveChannelException) {
            } catch (e: Throwable) {
                viaWebServer.onException(this, e)
                this.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "INTERNAL ERROR"))
            } finally {
                viaWebServer.disconnected(this)
            }
        }
    }

    private fun Route.routeApi() {
        route("/api/") {
            get("/getEpoch") {
                call.respond(System.currentTimeMillis() / 1000)
            }
        }
    }
}
