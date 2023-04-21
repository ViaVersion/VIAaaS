package com.viaversion.aas.protocol

import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.protocol.ProtocolManagerImpl
import net.raphimc.viaaprilfools.api.AprilFoolsProtocolVersion

val sharewareVersion = AprilFoolsProtocolVersion.s3d_shareware
fun registerAspirinProtocols() {
    // todo fix version checks for shareware

    // for ViaLegacy
    Via.getManager().protocolManager.maxProtocolPathSize = Int.MAX_VALUE
    Via.getManager().protocolManager.maxPathDeltaIncrease = -1
    (Via.getManager().protocolManager as ProtocolManagerImpl).refreshVersions()
}
