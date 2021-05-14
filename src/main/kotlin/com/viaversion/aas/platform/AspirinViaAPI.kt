package com.viaversion.aas.platform

import com.viaversion.viaversion.ViaAPIBase
import io.netty.buffer.ByteBuf
import java.util.*

object AspirinViaAPI : ViaAPIBase<UUID>() {
    override fun getPlayerVersion(p0: UUID): Int = super.getPlayerVersion(p0)
    override fun sendRawPacket(p0: UUID, p1: ByteBuf) = super.sendRawPacket(p0, p1)
}