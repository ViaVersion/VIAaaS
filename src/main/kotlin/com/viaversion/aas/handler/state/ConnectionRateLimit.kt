package com.viaversion.aas.handler.state

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.util.concurrent.RateLimiter
import com.viaversion.aas.config.VIAaaSConfig
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object ConnectionRateLimit {
    val rateLimitByIp = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(CacheLoader.from<InetAddress, LimitEntry> {
            createLimitEntryFromConfig()
        })

    fun createLimitEntryFromConfig(): LimitEntry {
        return LimitEntry(VIAaaSConfig.rateLimitConnectionMc, VIAaaSConfig.rateLimitLoginMc)
    }

    fun tryAcquireHandshake(address: InetAddress): Boolean {
        return rateLimitByIp[address].handshakeLimiter?.tryAcquire() ?: true
    }

    fun tryAcquireLogin(address: InetAddress): Boolean {
        return rateLimitByIp[address].loginLimiter?.tryAcquire() ?: true
    }

    class LimitEntry {
        val handshakeLimiter: RateLimiter?
        val loginLimiter: RateLimiter?

        constructor(hsLimit: Double, loginLimit: Double) {
            handshakeLimiter = if (hsLimit > 0) RateLimiter.create(hsLimit) else null
            loginLimiter = if (loginLimit > 0) RateLimiter.create(loginLimit) else null
        }
    }
}