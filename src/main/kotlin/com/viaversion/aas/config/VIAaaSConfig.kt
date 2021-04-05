package com.viaversion.aas.config

import us.myles.ViaVersion.util.Config
import java.io.File

object VIAaaSConfig : Config(File("config/viaaas.yml")) {
    init {
        reloadConfig()
    }

    override fun getUnsupportedOptions() = emptyList<String>().toMutableList()
    override fun getDefaultConfigURL() = VIAaaSConfig::class.java.classLoader.getResource("viaaas.yml")!!
    override fun handleConfig(p0: MutableMap<String, Any>?) {
    }

    val isNativeTransportMc: Boolean get() = this.getBoolean("native-transport-mc", true)
    val port: Int get() = this.getInt("port", 25565)
    val bindAddress: String get() = this.getString("bind-address", "localhost")!!
    val hostName: List<String> get() = this.getString("host-name", "viaaas.localhost")!!.split(",").map { it.trim() }
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
        )!!.map { it as String }
    val allowedBackAddresses: List<String>
        get() = this.get(
            "allowed-back-addresses",
            List::class.java,
            emptyList<String>()
        )!!.map { it as String }
    val forceOnlineMode: Boolean get() = this.getBoolean("force-online-mode", false)
    val showVersionPing: Boolean get() = this.getBoolean("show-version-ping", true)
    val showBrandInfo: Boolean get() = this.getBoolean("show-brand-info", true)
    val rateLimitWs: Double get() = this.getDouble("rate-limit-ws", 1.0)
    val rateLimitConnectionMc: Double get() = this.getDouble("rate-limit-connection-mc", 10.0)
    val listeningWsLimit: Int get() = this.getInt("listening-ws-limit", 16)
    val backendSocks5ProxyAddress: String?
        get() = this.getString("backend-socks5-proxy-address", "")!!.let { if (it.isEmpty()) null else it }
    val backendSocks5ProxyPort: Int get() = this.getInt("backend-socks5-proxy-port", 9050)
}
