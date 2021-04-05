package com.viaversion.aas.web

import com.viaversion.aas.viaWebServer
import com.viaversion.aas.webLogger
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import org.slf4j.event.Level
import java.nio.channels.ClosedChannelException
import java.time.Duration

class ViaWebApp {
    fun Application.main() {
        install(DefaultHeaders)
        install(ConditionalHeaders)
        install(CallLogging) {
            level = Level.INFO
            this.format {
                "${it.request.local.method.value} ${it.response.status()?.value} ${it.request.local.remoteHost} (O: ${it.request.origin.remoteHost}) " +
                        "${it.request.local.scheme}://${it.request.local.host}:${it.request.local.port}${it.request.local.uri}"
            }
        }
        install(WebSockets) {
            maxFrameSize = Short.MAX_VALUE.toLong()
            pingPeriod = Duration.ofSeconds(60)
            timeout = Duration.ofSeconds(15)
        }
        install(XForwardedHeaderSupport)
        install(ForwardedHeaderSupport)
        // i think we aren't vulnerable to breach, dynamic things are websockets
        // https://ktor.io/docs/compression.html#security
        install(Compression)

        routing {
            webSocket("/ws") {
                try {
                    viaWebServer.connected(this)
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            viaWebServer.onMessage(this, frame.readText())
                        }
                    }
                } catch (ignored: ClosedChannelException) {
                } catch (e: Exception) {
                    viaWebServer.onException(this, e)
                    this.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "INTERNAL ERROR"))
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
