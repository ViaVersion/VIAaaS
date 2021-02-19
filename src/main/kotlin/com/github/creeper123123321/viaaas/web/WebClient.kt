package com.github.creeper123123321.viaaas.web

import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.google.common.util.concurrent.RateLimiter
import io.ktor.websocket.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class WebClient(
    val server: WebDashboardServer,
    val ws: WebSocketServerSession,
    val state: WebState,
) {
    val listenedIds: MutableSet<UUID> = mutableSetOf()
    val rateLimiter = RateLimiter.create(VIAaaSConfig.rateLimitWs)

    fun listenId(uuid: UUID): Boolean {
        if (listenedIds.size >= VIAaaSConfig.listeningWsLimit) return false // This is getting insane
        server.listeners.computeIfAbsent(uuid) { Collections.newSetFromMap(ConcurrentHashMap()) }
            .add(this)
        listenedIds.add(uuid)
        return true
    }

    fun unlistenId(uuid: UUID) {
        server.listeners[uuid]?.remove(this)
        if (server.listeners[uuid]?.isEmpty() == true) {
            server.listeners.remove(uuid)
        }
        listenedIds.remove(uuid)
    }
}