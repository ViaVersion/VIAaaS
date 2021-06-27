package com.viaversion.aas.platform

import com.viaversion.aas.provider.AspirinVersionProvider
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.platform.ViaPlatformLoader
import com.viaversion.viaversion.api.protocol.version.VersionProvider
import com.viaversion.viaversion.protocols.protocol1_9to1_8.providers.MovementTransmitterProvider
import com.viaversion.viaversion.velocity.providers.VelocityMovementTransmitter

object AspirinLoader : ViaPlatformLoader {
    override fun unload() = Unit

    override fun load() {
        Via.getManager().providers.use(MovementTransmitterProvider::class.java, VelocityMovementTransmitter())
        Via.getManager().providers.use(VersionProvider::class.java, AspirinVersionProvider)
    }
}