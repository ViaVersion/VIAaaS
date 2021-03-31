package com.github.creeper123123321.viaaas.protocol

import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.remapper.ValueTransformer
import us.myles.ViaVersion.api.type.Type

val INSERT_DASHES: ValueTransformer<String, String> = object : ValueTransformer<String, String>(Type.STRING) {
    override fun transform(packetWrapper: PacketWrapper, s: String?): String {
        val builder = StringBuilder(s)
        builder.insert(20, "-")
        builder.insert(16, "-")
        builder.insert(12, "-")
        builder.insert(8, "-")
        return builder.toString()
    }
}