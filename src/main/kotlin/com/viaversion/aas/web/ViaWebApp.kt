package com.viaversion.aas.web

import com.viaversion.aas.AspirinServer
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.application.install
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
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
import kotlin.time.Duration.Companion.seconds

class ViaWebApp(val viaWebServer: WebServer) {
    fun Application.main() {
        install(DefaultHeaders) {
            header(
                "Content-Security-Policy",
                "default-src 'self';" +
                        "style-src 'self' https://cdnjs.cloudflare.com/;" +
                        "img-src 'self' data: https://crafthead.net/;" +
                        "connect-src 'self' http://localhost:*/ https: wss:;" +
                        "script-src 'self' https://cdnjs.cloudflare.com/ https://alcdn.msauth.net/ https://static.cloudflareinsights.com/;" +
                        "frame-src 'self' https://login.microsoftonline.com/ https://login.live.com/"
            )
            header("X-Robots-Tag", "noindex, nofollow")
            header("X-Frame-Options", "SAMEORIGIN")
            header("Referrer-Policy", "no-referrer")
        }
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
            pingPeriod = 20.seconds
            timeout = 15.seconds
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
            // https://ktor.io/docs/server-compression.html#security
            install(Compression) {
                gzip {
                    minimumSize(4096)
                    matchContentType(
                        ContentType.Text.Html,
                        ContentType.Text.JavaScript,
                        ContentType.Text.CSS
                    )
                }
            }
            install(CachingHeaders) {
                options { _, content ->
                    when (content.contentType?.withoutParameters()) {
                        ContentType.Text.Html,
                        ContentType.Text.JavaScript,
                        ContentType.Text.CSS -> CachingOptions(CacheControl.NoCache(CacheControl.Visibility.Public))

                        else -> null
                    }
                }
            }
            install(ConditionalHeaders)
            install(PartialContent)
            get("{path...}") {
                serveFileOrResource()
            }
        }
    }

    private suspend fun RoutingContext.serveFileOrResource() {
        val relativePath = Path.of(call.parameters.getAll("path")?.joinToString("/") ?: "")
        val index = Path.of("index.html")

        var file = File("config/web/").combineSafe(relativePath)
        if (file.isDirectory) {
            file = file.resolve(index.toFile())
        }

        if (file.isFile) {
            call.respondFile(file)
            return
        }

        val resource = call.resolveResource(relativePath.toString(), "web")
            ?: call.resolveResource(relativePath.resolve(index).toString(), "web")

        if (resource != null) {
            resource.versions += EntityTagVersion("resource." + AspirinServer.version, weak = true)
            call.respond(resource)
            return
        }

        call.respond(HttpStatusCode.NotFound, "404 Not Found")
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
            install(CachingHeaders) {
                options { _, _ ->
                    CachingOptions(CacheControl.NoStore(CacheControl.Visibility.Private))
                }
            }
            get("/getEpoch") {
                call.respond(System.currentTimeMillis() / 1000)
            }
        }
    }
}
