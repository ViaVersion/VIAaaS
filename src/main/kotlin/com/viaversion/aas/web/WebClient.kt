package com.viaversion.aas.web

import com.viaversion.aas.config.VIAaaSConfig
import com.google.common.collect.Sets
import com.google.common.util.concurrent.RateLimiter
import io.ktor.features.*
import io.ktor.websocket.*
import java.util.*
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

    val id = "${ws.call.request.local.remoteHost}(${ws.call.request.origin.remoteHost})-${IdGen.next()}"
    val listenedIds: MutableSet<UUID> = Sets.newConcurrentHashSet()
    val rateLimiter = RateLimiter.create(VIAaaSConfig.rateLimitWs)

    fun listenId(uuid: UUID): Boolean {
        if (listenedIds.size >= VIAaaSConfig.listeningWsLimit) return false // This is getting insane
        listenedIds.add(uuid)
        return server.listeners.put(uuid, this)
    }

    fun unlistenId(uuid: UUID): Boolean {
        server.listeners.remove(uuid, this)
        return listenedIds.remove(uuid)
    }
}
