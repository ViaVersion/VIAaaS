package com.github.creeper123123321.viaaas

import us.myles.ViaVersion.api.protocol.ProtocolVersion
import java.util.*

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

        var endOfOptions = false
        val optionsList = arrayListOf<String>()
        serverAddress = suffixRemoved.split('.').asReversed().filter {
            if (endOfOptions || !parseOption(it)) {
                endOfOptions = true
                true
            } else {
                optionsList.add(it)
                false
            }
        }.asReversed().joinToString(".")

        viaOptions = optionsList.asReversed().joinToString(".")
        viaSuffix = viaHostName

        return this
    }

    fun parseOption(part: String): Boolean {
        val option = when {
                part.length < 2 -> null
                part.startsWith("_") -> part[1]
                part[1] == '_' -> part[0]
                else -> null
            }?.toString()
        if (option != null) {
            val arg = part.substring(2)
            when (option.toLowerCase(Locale.ROOT)) {
                "p" -> port = arg.toInt()
                "o" -> online = when {
                        arg.startsWith("t", ignoreCase = true) -> true
                        arg.startsWith("f", ignoreCase = true) -> false
                        else -> null
                    }
                "v" -> parseProtocol(arg)
                "u" -> {
                    if (arg.length > 16) throw IllegalArgumentException("Invalid username")
                    username = arg
                }
            }
            return true
        }
        return false
    }

    private fun parseProtocol(arg: String) {
        try {
            protocol = arg.toInt()
        } catch (e: NumberFormatException) {
            ProtocolVersion.getClosest(arg.replace("_", "."))?.also {
                protocol = it.version
            }
        }
        if (protocol == -2) {
            protocol = null
        }
    }
}