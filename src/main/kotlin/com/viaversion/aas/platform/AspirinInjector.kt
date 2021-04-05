package com.viaversion.aas.platform

import us.myles.ViaVersion.api.platform.ViaInjector
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.viaversion.libs.fastutil.ints.IntLinkedOpenHashSet
import us.myles.viaversion.libs.fastutil.ints.IntSortedSet
import us.myles.viaversion.libs.gson.JsonObject

object AspirinInjector : ViaInjector {
    override fun getEncoderName(): String = "via-codec"
    override fun getDecoderName() = "via-codec"
    override fun getDump(): JsonObject = JsonObject()
    override fun getServerProtocolVersions(): IntSortedSet = IntLinkedOpenHashSet(
        sortedSetOf(ProtocolVersion.v1_16_4.version, ProtocolVersion.v1_7_1.version)
    )

    override fun getServerProtocolVersion(): Int = ProtocolVersion.v1_7_1.version

    override fun uninject() {
    }

    override fun inject() {
    }
}