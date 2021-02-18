package com.github.creeper123123321.viaaas.web

import com.github.creeper123123321.viaaas.viaWebServer
import com.github.creeper123123321.viaaas.webLogger
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach
import org.slf4j.event.Level
import java.nio.channels.ClosedChannelException

class ViaWebApp {
    fun Application.main() {
        install(DefaultHeaders)
        install(CallLogging) {
            level = Level.INFO
        }
        install(WebSockets) {
            maxFrameSize = Short.MAX_VALUE.toLong()
            pingPeriod = Duration.ofSeconds(60)
            timeout = Duration.ofSeconds(15)
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
                } catch (ignored: ClosedChannelException) {
                } catch (e: Exception) {
                    webLogger.info("${call.request.local.remoteHost} (O: ${call.request.origin.remoteHost}) exception: $e")
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
