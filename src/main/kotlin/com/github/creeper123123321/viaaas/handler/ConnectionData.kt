package com.github.creeper123123321.viaaas.handler

import com.github.creeper123123321.viaaas.handler.state.HandshakeState
import com.github.creeper123123321.viaaas.handler.state.MinecraftConnectionState
import io.netty.channel.Channel

class ConnectionData(
    val frontChannel: Channel,
    var backChannel: Channel? = null,
    var state: MinecraftConnectionState = HandshakeState(),
    var frontOnline: Boolean? = null, // todo
    var frontName: String? = null,
    var backName: String? = null,
    var frontVer: Int? = null,
    var backVer: Int? = null,
) {
    val frontHandler get() = frontChannel.pipeline().get(MinecraftHandler::class.java)
    val backHandler get() = backChannel?.pipeline()?.get(MinecraftHandler::class.java)
}