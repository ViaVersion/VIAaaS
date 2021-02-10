package com.github.creeper123123321.viaaas.web

import io.ktor.websocket.*
import java.util.*

data class WebClient(
    val server: WebDashboardServer,
    val ws: WebSocketServerSession,
    val state: WebState,
    val listenedIds: MutableSet<UUID> = mutableSetOf()
)