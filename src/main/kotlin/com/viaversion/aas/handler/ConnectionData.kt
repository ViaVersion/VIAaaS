package com.viaversion.aas.handler

import com.viaversion.aas.handler.state.HandshakeState
import com.viaversion.aas.handler.state.MinecraftConnectionState
import io.netty.channel.Channel

class ConnectionData(
    val frontChannel: Channel,
    var backChannel: Channel? = null,
    var state: MinecraftConnectionState = HandshakeState(),
    var frontVer: Int? = null,
    var viaBackServerVer: Int? = null,
) {
    val frontHandler get() = frontChannel.pipeline().get(MinecraftHandler::class.java)
    val backHandler get() = backChannel?.pipeline()?.get(MinecraftHandler::class.java)
}