package com.viaversion.aas.config

import com.google.common.net.HostAndPort
import com.viaversion.aas.secureRandom
import com.viaversion.aas.util.AddressParser
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
    var defaultParameters: Map<Int, AddressParser> = emptyMap()
    var bindAddresses = emptyList<HostAndPort>()
    var hostName: List<String> = emptyList()
    var blockLocalAddress = true
    var requireHostName: Boolean = true
    var defaultBackendPort: Int? = null
    var blockedBackAddresses: List<String> = emptyList()
    var allowedBackAddresses: List<String> = emptyList()
    var forceOnlineMode: Boolean = false
    var showVersionPing: Boolean = true
    var showBrandInfo: Boolean = true
    var rateLimitWs: Double = 1.0
    var rateLimitConnectionMc: Double = 10.0
    var listeningWsLimit: Int = 16
    var jwtSecret: String = ""
    var rateLimitLoginMc: Double = 0.2
    var faviconUrl: String? = null
    var maxPlayers: Int? = null
    var backendProxy: URI? = null
    var protocolDetectorCache: Int = 30
    var compressionLevel: Int = 6
    var bungeeCord: Boolean = false

    init {
        reloadConfig()
    }

    override fun reloadConfig() {
        super.reloadConfig()
        reloadFields()
    }

    private fun reloadFields() {
        reloadIcon()
        defaultParameters = this.get("default-parameters", Map::class.java, emptyMap<Int, String>())!!.map {
            (it.key as Number).toInt() to AddressParser().parse(it.value.toString())
        }.toMap()
        bindAddresses = this.getStringList("bind-addresses").map { HostAndPort.fromString(it).withDefaultPort(25565) }
        hostName = this.get("host-name", List::class.java, emptyList<String>())!!.map { it.toString() }
        blockLocalAddress = this.getBoolean("block-local-address", true)
        requireHostName = this.getBoolean("require-host-name", true)
        defaultBackendPort = this.getInt("default-backend-port", 25565).let { if (it == -1) null else it }
        blockedBackAddresses = this.get(
            "blocked-back-addresses",
            List::class.java,
            emptyList<String>()
        )!!.map { it.toString() }
        allowedBackAddresses = this.get(
            "allowed-back-addresses",
            List::class.java,
            emptyList<String>()
        )!!.map { it.toString() }
        forceOnlineMode = this.getBoolean("force-online-mode", false)
        showVersionPing = this.getBoolean("show-version-ping", true)
        showBrandInfo = this.getBoolean("show-brand-info", true)
        rateLimitWs = this.getDouble("rate-limit-ws", 1.0)
        rateLimitConnectionMc = this.getDouble("rate-limit-connection-mc", 10.0)
        listeningWsLimit = this.getInt("listening-ws-limit", 16)
        jwtSecret = this.getString("jwt-secret", null).let {
            if (it.isNullOrBlank()) throw IllegalStateException("invalid jwt-secret") else it
        }
        rateLimitLoginMc = this.getDouble("rate-limit-login-mc", 0.2)
        maxPlayers = this.getInt("max-players", 20).let { if (it == -1) null else it }
        backendProxy = this.getString("backend-proxy", "").let { if (it.isNullOrEmpty()) null else URI.create(it) }
        protocolDetectorCache = this.getInt("protocol-detector-cache", 30)
        compressionLevel = this.getInt("compression-level", 6)
        bungeeCord = this.getBoolean("bungee-cord", false)
    }

    fun reloadIcon() {
        val rawUrl = this.getString("favicon-url", "")!!
        try {
            faviconUrl = when {
                rawUrl.isEmpty() -> null
                rawUrl.startsWith("data:image/png;base64,") -> rawUrl.filter { !it.isWhitespace() }
                else -> "data:image/png;base64," + Base64.getEncoder().encodeToString(
                    ByteArrayOutputStream().also {
                        Thumbnails.of(URL(rawUrl))
                            .size(64, 64)
                            .addFilter(Canvas(64, 64, Positions.CENTER, false))
                            .outputFormat("png")
                            .toOutputStream(it)
                    }.toByteArray()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun getUnsupportedOptions() = emptyList<String>()
    override fun getDefaultConfigURL() = VIAaaSConfig::class.java.classLoader.getResource("viaaas.yml")!!
    override fun handleConfig(map: MutableMap<String, Any>) {
        fixConfig(map)
        upgradeConfig(map)
    }

    private fun fixConfig(map: MutableMap<String, Any>) {
        if (map["jwt-secret"]?.toString().isNullOrBlank()) {
            map["jwt-secret"] = Base64.getEncoder()
                .encodeToString(ByteArray(64)
                    .also { secureRandom.nextBytes(it) })
        }
    }

    private fun upgradeConfig(map: MutableMap<String, Any>) {
        if (map["host-name"] is String) {
            map["host-name"] = map["host-name"].toString().split(',').map { it.trim() }
        }

        val oldSocks = map.remove("backend-socks5-proxy-address")
        val oldSocksPort = map.remove("backend-socks5-proxy-port")
        if (oldSocks is String && oldSocks.isNotBlank()) {
            map["backend-proxy"] = "socks5://${HostAndPort.fromParts(oldSocks, oldSocksPort.toString().toInt())}"
        }

        val oldBind = map.remove("bind-address")?.toString()
        val oldPort = map.remove("port")?.toString()
        if (!oldBind.isNullOrEmpty() && !oldPort.isNullOrEmpty()) {
            map["bind-addresses"] = listOf(HostAndPort.fromParts(oldBind, oldPort.toInt()).toString())
        }
    }
}
