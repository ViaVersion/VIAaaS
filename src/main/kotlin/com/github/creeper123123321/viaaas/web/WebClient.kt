package com.github.creeper123123321.viaaas.web

import com.github.creeper123123321.viaaas.config.VIAaaSConfig
import com.google.common.collect.Sets
import com.google.common.util.concurrent.RateLimiter
import io.ktor.features.*
import io.ktor.websocket.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class WebClient(
    val server: WebDashboardServer,
    val ws: WebSocketServerSession,
    val state: WebState,
) {
    object IdGen {
        val atInt = AtomicInteger()
        fun next() = atInt.getAndAdd(1)
    }
    val id = "${ws.call.request.local.host}(${ws.call.request.origin.host})-${IdGen.next()}"
    val listenedIds: MutableSet<UUID> = Sets.newConcurrentHashSet()
    val rateLimiter = RateLimiter.create(VIAaaSConfig.rateLimitWs)

    fun listenId(uuid: UUID): Boolean {
        if (listenedIds.size >= VIAaaSConfig.listeningWsLimit) return false // This is getting insane
        server.listeners.computeIfAbsent(uuid) { Sets.newConcurrentHashSet() }.add(this)
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
