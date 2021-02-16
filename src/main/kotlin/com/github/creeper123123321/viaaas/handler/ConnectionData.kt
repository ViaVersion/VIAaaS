package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.handler.state.HandshakeState
import com.github.creeper123123321.viaaas.handler.state.MinecraftConnectionState
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