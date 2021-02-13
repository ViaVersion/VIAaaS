package com.github.creeper123123321.viaaas

import us.myles.ViaVersion.api.protocol.ProtocolVersion

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
        if (part.startsWith("_")) {
            val arg = part.substring(2)
            when {
                part.startsWith("_p", ignoreCase = true) -> port = arg.toInt()
                part.startsWith("_o", ignoreCase = true) -> {
                    online = when {
                        arg.startsWith("t", ignoreCase = true) -> true
                        arg.startsWith("f", ignoreCase = true) -> false
                        else -> null
                    }
                }
                part.startsWith("_v", ignoreCase = true) -> {
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
                part.startsWith("_u", ignoreCase = true) -> {
                    if (arg.length > 16) throw IllegalArgumentException("Invalid username")
                    username = arg
                }
            }
            return true
        }
        return false
    }
}