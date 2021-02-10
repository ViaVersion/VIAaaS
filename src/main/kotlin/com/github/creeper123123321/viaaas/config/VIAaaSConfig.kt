package com.github.creeper123123321.viaaas.config

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
    val hostName: String get() = this.getString("host-name", "viaaas.localhost")!!
    val mcRsaSize: Int get() = this.getInt("mc-rsa-size", 4096)
    val useStrongRandom: Boolean get() = this.getBoolean("use-strong-random", true)
    val blockLocalAddress: Boolean get() = this.getBoolean("block-local-address", true)
    val requireHostName: Boolean get() = this.getBoolean("require-host-name", true)
    val defaultBackendPort: Int get() = this.getInt("default-backend-port", 25565)
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
}