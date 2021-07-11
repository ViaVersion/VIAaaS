package com.viaversion.aas.handler

import com.viaversion.aas.codec.CryptoCodec
import com.viaversion.aas.handler.state.HandshakeState
import com.viaversion.aas.handler.state.ConnectionState
import io.netty.channel.Channel

class ConnectionData(
    val frontChannel: Channel,
    var backChannel: Channel? = null,
    var state: ConnectionState = HandshakeState(),
    var frontVer: Int? = null,
    var backServerVer: Int? = null,
) {
    val frontHandler get() = frontChannel.pipeline().get(MinecraftHandler::class.java)
    val backHandler get() = backChannel?.pipeline()?.get(MinecraftHandler::class.java)
    val frontEncrypted get() = frontChannel.pipeline().get(CryptoCodec::class.java) != null
}