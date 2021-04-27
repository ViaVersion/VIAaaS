package com.viaversion.aas.platform

import com.viaversion.viaversion.api.boss.BossColor
import com.viaversion.viaversion.api.boss.BossStyle
import com.viaversion.viaversion.boss.CommonBoss

class AspirinBossBar(title: String, health: Float, style: BossStyle, color: BossColor) :
    CommonBoss<Unit>(title, health, color, style)