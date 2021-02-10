package com.github.creeper123123321.viaaas.platform

import us.myles.ViaVersion.api.platform.ViaInjector
import us.myles.viaversion.libs.gson.JsonObject

object CloudInjector : ViaInjector {
    override fun getEncoderName(): String = "via-codec"
    override fun getDecoderName() = "via-codec"
    override fun getDump(): JsonObject = JsonObject()

    override fun uninject() {
    }

    override fun inject() {
    }


    override fun getServerProtocolVersion() = 47 // Dummy
}