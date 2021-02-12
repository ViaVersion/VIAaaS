package com.github.creeper123123321.viaaas.platform

import us.myles.ViaVersion.api.boss.BossColor
import us.myles.ViaVersion.api.boss.BossStyle
import us.myles.ViaVersion.boss.CommonBoss

class AspirinBossBar(title: String, health: Float, style: BossStyle, color: BossColor) :
    CommonBoss<Unit>(title, health, color, style)