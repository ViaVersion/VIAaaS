package com.viaversion.aas.platform

import com.viaversion.aas.provider.AspirinVersionProvider
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.platform.ViaPlatformLoader
import com.viaversion.viaversion.api.protocol.version.VersionProvider
import com.viaversion.viaversion.bungee.providers.BungeeMovementTransmitter
import com.viaversion.viaversion.protocols.protocol1_9to1_8.providers.MovementTransmitterProvider

object AspirinLoader : ViaPlatformLoader {
    override fun unload() {
    }

    override fun load() {
        Via.getManager().providers.use(MovementTransmitterProvider::class.java, BungeeMovementTransmitter())
        Via.getManager().providers.use(VersionProvider::class.java, AspirinVersionProvider)
    }
}