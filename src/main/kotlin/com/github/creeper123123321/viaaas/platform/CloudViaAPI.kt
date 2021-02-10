package com.github.creeper123123321.viaaas.platform

import io.netty.buffer.ByteBuf
import us.myles.ViaVersion.api.ViaAPI
import us.myles.ViaVersion.api.boss.BossBar
import us.myles.ViaVersion.api.boss.BossColor
import us.myles.ViaVersion.api.boss.BossStyle
import us.myles.ViaVersion.api.protocol.ProtocolRegistry
import java.util.*
import kotlin.UnsupportedOperationException

object CloudViaAPI : ViaAPI<Unit> {
    override fun isInjected(p0: UUID): Boolean = false
    override fun createBossBar(p0: String, p1: BossColor, p2: BossStyle): BossBar<*> = CloudBossBar(p0, 0f, p2, p1)
    override fun createBossBar(p0: String, p1: Float, p2: BossColor, p3: BossStyle): BossBar<*> = CloudBossBar(p0, p1, p3, p2)
    override fun sendRawPacket(p0: Unit?, p1: ByteBuf?) = throw UnsupportedOperationException()
    override fun sendRawPacket(p0: UUID?, p1: ByteBuf?) = throw UnsupportedOperationException()
    override fun getPlayerVersion(p0: Unit?): Int = throw UnsupportedOperationException()
    override fun getPlayerVersion(p0: UUID?): Int = throw UnsupportedOperationException()
    override fun getVersion(): String = CloudPlatform.pluginVersion
    override fun getSupportedVersions(): SortedSet<Int> = ProtocolRegistry.getSupportedVersions()
}