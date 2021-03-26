package com.github.creeper123123321.viaaas.platform

import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.ViaAPIBase
import us.myles.ViaVersion.api.boss.BossBar
import us.myles.ViaVersion.api.boss.BossColor
import us.myles.ViaVersion.api.boss.BossStyle
import java.util.*

object AspirinViaAPI : ViaAPIBase<UUID>() {
    override fun createBossBar(p0: String, p1: BossColor, p2: BossStyle): BossBar<*> = AspirinBossBar(p0, 0f, p2, p1)
    override fun createBossBar(p0: String, p1: Float, p2: BossColor, p3: BossStyle): BossBar<*> =
        AspirinBossBar(p0, p1, p3, p2)

    override fun sendRawPacket(p0: UUID, p1: ByteBuf) = super.sendRawPacket(p0, p1)
    override fun getPlayerVersion(p0: UUID?): Int = throw UnsupportedOperationException()
}