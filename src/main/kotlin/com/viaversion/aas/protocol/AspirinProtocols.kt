package com.viaversion.aas.protocol

import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.protocol.ProtocolManagerImpl

fun registerAspirinProtocols() {
    // for ViaLegacy
    Via.getManager().protocolManager.maxProtocolPathSize = Int.MAX_VALUE
    Via.getManager().protocolManager.maxPathDeltaIncrease = -1
    (Via.getManager().protocolManager as ProtocolManagerImpl).refreshVersions()
}
