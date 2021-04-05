package com.viaversion.aas.platform

import com.viaversion.aas.provider.AspirinVersionProvider
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.platform.ViaPlatformLoader
import us.myles.ViaVersion.bungee.providers.BungeeMovementTransmitter
import us.myles.ViaVersion.protocols.base.VersionProvider
import us.myles.ViaVersion.protocols.protocol1_9to1_8.providers.MovementTransmitterProvider

object AspirinLoader : ViaPlatformLoader {
    override fun unload() {
    }

    override fun load() {
        Via.getManager().providers.use(MovementTransmitterProvider::class.java, BungeeMovementTransmitter())
        Via.getManager().providers.use(VersionProvider::class.java, AspirinVersionProvider)
    }
}