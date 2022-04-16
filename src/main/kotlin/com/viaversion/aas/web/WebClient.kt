package com.viaversion.aas.web

import com.google.common.collect.Sets
import com.google.common.util.concurrent.RateLimiter
import com.viaversion.aas.config.VIAaaSConfig
import io.ktor.server.plugins.*
import io.ktor.server.websocket.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

data class WebClient(
    val server: WebServer,
    val ws: WebSocketServerSession,
    val state: WebState,
) {
    object IdGen {
        val atInt = AtomicInteger()
        fun next() = atInt.getAndAdd(1)
    }

    val id = run {
        val local = ws.call.request.local.remoteHost
        val remote = ws.call.request.origin.remoteHost
        "$local${if (local != remote) "|$remote" else ""}-${IdGen.next()}"
    }
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
