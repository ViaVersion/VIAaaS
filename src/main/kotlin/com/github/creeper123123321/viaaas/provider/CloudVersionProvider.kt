package com.github.creeper123123321.viaaas.provider

import com.github.creeper123123321.viaaas.handler.CloudMinecraftHandler
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.protocols.base.VersionProvider

object CloudVersionProvider : VersionProvider() {
    override fun getServerProtocol(connection: UserConnection): Int {
        val ver = connection.channel!!.pipeline().get(CloudMinecraftHandler::class.java).data.backVer
        if (ver != null) return ver
        return super.getServerProtocol(connection)
    }
}