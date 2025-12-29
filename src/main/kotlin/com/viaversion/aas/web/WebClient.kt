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

    val id = generateId()
    val challenge = UUID.randomUUID().toString()
    private val listenedIds: MutableSet<UUID> = Sets.newConcurrentHashSet()
    private val rateLimiter = createRateLimiter()

    fun tryAcquireMessage() : Boolean {
        return rateLimiter?.tryAcquire() ?: true
    }

    fun createRateLimiter(): RateLimiter? {
        val limit = VIAaaSConfig.rateLimitWs
        if (limit <= 0) return null
        return RateLimiter.create(limit)
    }


    fun generateId(): String {
        val local = ws.call.request.local.remoteHost
        val remote = ws.call.request.origin.remoteHost
        return "$local${if (local != remote) "|$remote" else ""}-${IdGen.next()}"
    }

    fun listenId(uuid: UUID): Boolean {
        if (listenedIds.size >= VIAaaSConfig.listeningWsLimit) return false // This is getting insane
        listenedIds.add(uuid)
        return server.addListener(uuid, this)
    }
    fun unlistenId(uuid: UUID): Boolean {
        server.removeListener(uuid, this)
        return listenedIds.remove(uuid)
    }
    fun unlistenAll() {
        listenedIds.forEach { unlistenId(it) }
    }
}
