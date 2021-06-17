package com.viaversion.aas

import com.viaversion.aas.util.StacklessException
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion

class VIAaaSAddress {
    var serverAddress: String? = null
    var viaSuffix: String? = null
    var viaOptions: String? = null
    var protocol: Int? = null
    var port: Int? = null
    var online: Boolean? = null
    var username: String? = null
    fun parse(rawAddress: String, viaHostName: String): VIAaaSAddress {
        val address = rawAddress.removeSuffix(".")
        val suffixRemoved = address.removeSuffix(".$viaHostName")

        if (suffixRemoved == address) {
            serverAddress = address
            return this
        }

        var stopOptions = false
        val optionsParts = arrayListOf<String>()
        val serverParts = arrayListOf<String>()

        for (part in suffixRemoved.split('.').asReversed()) {
            if (!stopOptions && parseOption(part)) {
                optionsParts.add(part)
                continue
            }
            stopOptions = true
            serverParts.add(part)
        }

        serverAddress = serverParts.asReversed().joinToString(".")
        viaOptions = optionsParts.asReversed().joinToString(".")
        viaSuffix = viaHostName

        return this
    }

    fun parseOption(part: String): Boolean {
        val option = when {
            part.length < 2 -> null
            part.startsWith("_") -> part[1]
            part[1] == '_' -> part[0]
            else -> null
        }?.toString() ?: return false

        val arg = part.substring(2)
        when (option.lowercase()) {
            "p" -> parsePort(arg)
            "o" -> parseOnlineMode(arg)
            "v" -> parseProtocol(arg)
            "u" -> parseUsername(arg)
        }
        return true
    }

    fun parsePort(arg: String) {
        port = arg.toInt()
    }

    fun parseUsername(arg: String) {
        if (arg.length > 16) throw StacklessException("Invalid username")
        username = arg
    }

    fun parseOnlineMode(arg: String) {
        online = when {
            arg.startsWith("t", ignoreCase = true) -> true
            arg.startsWith("f", ignoreCase = true) -> false
            else -> null
        }
    }

    fun parseProtocol(arg: String) {
        try {
            protocol = arg.toInt()
        } catch (e: NumberFormatException) {
            ProtocolVersion.getClosest(arg.replace("_", "."))?.also {
                protocol = it.version
            }
        }
    }
}
