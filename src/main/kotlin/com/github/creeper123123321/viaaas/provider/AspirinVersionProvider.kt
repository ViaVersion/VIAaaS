package com.github.creeper123123321.viaaas.provider

import com.github.creeper123123321.viaaas.handler.MinecraftHandler
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.protocols.base.BaseVersionProvider

object AspirinVersionProvider : BaseVersionProvider() {
    override fun getServerProtocol(connection: UserConnection): Int {
        val ver = connection.channel!!.pipeline().get(MinecraftHandler::class.java).data.viaBackServerVer
        if (ver != null) return ver
        return super.getServerProtocol(connection)
    }
}