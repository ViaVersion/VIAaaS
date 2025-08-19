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
        .build(CacheLoader.from<InetAddress, Limits> {
            Limits(
                RateLimiter.create(VIAaaSConfig.rateLimitConnectionMc),
                RateLimiter.create(VIAaaSConfig.rateLimitLoginMc)
            )
        })

    fun tryAcquireHandshake(address: InetAddress): Boolean {
        return rateLimitByIp[address].handshakeLimiter.tryAcquire()
    }

    fun tryAcquireLogin(address: InetAddress): Boolean {
        return rateLimitByIp[address].loginLimiter.tryAcquire()
    }

    data class Limits(val handshakeLimiter: RateLimiter, val loginLimiter: RateLimiter)
}