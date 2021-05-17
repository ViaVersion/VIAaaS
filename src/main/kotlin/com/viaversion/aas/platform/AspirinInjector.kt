package com.viaversion.aas.platform

import com.viaversion.viaversion.api.platform.ViaInjector
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.libs.fastutil.ints.IntLinkedOpenHashSet
import com.viaversion.viaversion.libs.fastutil.ints.IntSortedSet
import com.viaversion.viaversion.libs.gson.JsonObject

object AspirinInjector : ViaInjector {
    override fun getEncoderName(): String = "via-codec"
    override fun getDecoderName() = "via-codec"
    override fun getDump(): JsonObject = JsonObject()
    override fun getServerProtocolVersions(): IntSortedSet = IntLinkedOpenHashSet(
        sortedSetOf(
            ProtocolVersion.getProtocols().maxOf { it.originalVersion },
            ProtocolVersion.v1_7_1.version
        )
    )

    override fun getServerProtocolVersion(): Int = ProtocolVersion.v1_7_1.version

    override fun uninject() {
    }

    override fun inject() {
    }
}