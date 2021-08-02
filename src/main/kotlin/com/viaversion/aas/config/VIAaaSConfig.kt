package com.viaversion.aas.config

import com.viaversion.aas.secureRandom
import com.viaversion.viaversion.util.Config
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.filters.Canvas
import net.coobird.thumbnailator.geometry.Positions
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.net.URL
import java.util.*

object VIAaaSConfig : Config(File("config/viaaas.yml")) {
    init {
        reloadConfig()
    }

    override fun reloadConfig() {
        super.reloadConfig()
        reloadIcon()
    }

    fun reloadIcon() {
        val rawUrl = this.getString("favicon-url", "")!!
        faviconUrl = when {
            rawUrl.isEmpty() -> null
            rawUrl.startsWith("data:image/png;base64,") -> rawUrl.filter { !it.isWhitespace() }
            else -> "data:image/png;base64," + Base64.getEncoder().encodeToString(
                ByteArrayOutputStream().also {
                    Thumbnails.of(URL(rawUrl))
                        .size(64, 64)
                        .addFilter(Canvas(64, 64, Positions.CENTER, false))
                        .outputFormat("png").toOutputStream(it)
                }.toByteArray()
            )
        }
    }

    override fun getUnsupportedOptions() = emptyList<String>()
    override fun getDefaultConfigURL() = VIAaaSConfig::class.java.classLoader.getResource("viaaas.yml")!!
    override fun handleConfig(map: MutableMap<String, Any>) {
        // Migration from older config versions
        if (map["jwt-secret"]?.toString().isNullOrBlank()) {
            map["jwt-secret"] = Base64.getEncoder()
                .encodeToString(ByteArray(64)
                    .also { secureRandom.nextBytes(it) })
        }

        if (map["host-name"] is String) {
            map["host-name"] = map["host-name"].toString().split(',').map { it.trim() }
        }

        val oldSocks = map.remove("backend-socks5-proxy-address")
        val oldSocksPort = map.remove("backend-socks5-proxy-port")
        if (oldSocks is String && oldSocks.isNotBlank()) {
            map["backend-proxy"] = "socks5://$oldSocks:$oldSocksPort"
        }
    }

    val port: Int get() = this.getInt("port", 25565)
    val bindAddress: String get() = this.getString("bind-address", "localhost")!!
    val hostName: List<String>
        get() = this.get("host-name", List::class.java, listOf("viaaas.localhost"))!!.map { it.toString() }
    val mcRsaSize: Int get() = this.getInt("mc-rsa-size", 4096)
    val blockLocalAddress: Boolean get() = this.getBoolean("block-local-address", true)
    val requireHostName: Boolean get() = this.getBoolean("require-host-name", true)
    val defaultBackendPort: Int? get() = this.getInt("default-backend-port", 25565).let { if (it == -1) null else it }
    val blockedBackAddresses: List<String>
        get() = this.get(
            "blocked-back-addresses",
            List::class.java,
            emptyList<String>()
        )!!.map { it.toString() }
    val allowedBackAddresses: List<String>
        get() = this.get(
            "allowed-back-addresses",
            List::class.java,
            emptyList<String>()
        )!!.map { it.toString() }
    val forceOnlineMode: Boolean get() = this.getBoolean("force-online-mode", false)
    val showVersionPing: Boolean get() = this.getBoolean("show-version-ping", true)
    val showBrandInfo: Boolean get() = this.getBoolean("show-brand-info", true)
    val rateLimitWs: Double get() = this.getDouble("rate-limit-ws", 1.0)
    val rateLimitConnectionMc: Double get() = this.getDouble("rate-limit-connection-mc", 10.0)
    val listeningWsLimit: Int get() = this.getInt("listening-ws-limit", 16)
    val jwtSecret: String
        get() = this.getString("jwt-secret", null).let {
            if (it.isNullOrBlank()) throw IllegalStateException("invalid jwt-secret") else it
        }
    val rateLimitLoginMc: Double get() = this.getDouble("rate-limit-login-mc", 0.2)
    var faviconUrl: String? = null
    val maxPlayers: Int? get() = this.getInt("max-players", 20).let { if (it == -1) null else it }
    val backendProxy: URI?
        get() = this.getString("backend-proxy", "").let { if (it.isNullOrEmpty()) null else URI.create(it) }
    val protocolDetectorCache: Int
        get() = this.getInt("protocol-detector-cache", 30)
}
