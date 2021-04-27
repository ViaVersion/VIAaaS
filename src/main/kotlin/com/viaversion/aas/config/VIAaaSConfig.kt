package com.viaversion.aas.config

import com.viaversion.viaversion.util.Config
import java.io.File
import java.security.SecureRandom
import java.util.*

object VIAaaSConfig : Config(File("config/viaaas.yml")) {
    init {
        reloadConfig()
    }

    override fun getUnsupportedOptions() = emptyList<String>().toMutableList()
    override fun getDefaultConfigURL() = VIAaaSConfig::class.java.classLoader.getResource("viaaas.yml")!!
    override fun handleConfig(map: MutableMap<String, Any>) {
        if (map["jwt-secret"]?.toString().isNullOrBlank()) {
            map["jwt-secret"] = Base64.getEncoder().encodeToString(ByteArray(64)
                .also { SecureRandom().nextBytes(it) })
        }

        if (map["host-name"] is String) {
            map["host-name"] = map["host-name"].toString().split(',').map { it.trim() }
        }
    }

    val isNativeTransportMc: Boolean get() = this.getBoolean("native-transport-mc", true)
    val port: Int get() = this.getInt("port", 25565)
    val bindAddress: String get() = this.getString("bind-address", "localhost")!!
    val hostName: List<String>
        get() = this.get("host-name", List::class.java, listOf("viaaas.localhost"))!!.map { it.toString() }
    val mcRsaSize: Int get() = this.getInt("mc-rsa-size", 4096)
    val useStrongRandom: Boolean get() = this.getBoolean("use-strong-random", true)
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
    val backendSocks5ProxyAddress: String?
        get() = this.getString("backend-socks5-proxy-address", "")!!.ifEmpty { null }
    val backendSocks5ProxyPort: Int get() = this.getInt("backend-socks5-proxy-port", 9050)
    val jwtSecret: String
        get() = this.getString("jwt-secret", null).let {
            if (it.isNullOrBlank()) throw IllegalStateException("invalid jwt-secret") else it
        }
}
