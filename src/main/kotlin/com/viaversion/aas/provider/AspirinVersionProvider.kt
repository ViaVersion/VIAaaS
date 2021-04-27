package com.viaversion.aas.provider

import com.viaversion.aas.handler.MinecraftHandler
import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.protocols.base.BaseVersionProvider

object AspirinVersionProvider : BaseVersionProvider() {
    override fun getClosestServerProtocol(connection: UserConnection): Int {
        val ver = connection.channel!!.pipeline().get(MinecraftHandler::class.java).data.viaBackServerVer
        if (ver != null) return ver
        return super.getClosestServerProtocol(connection)
    }
}
