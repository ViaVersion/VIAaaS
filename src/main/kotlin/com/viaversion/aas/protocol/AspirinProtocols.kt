package com.viaversion.aas.protocol

import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import net.raphimc.viaaprilfools.api.AprilFoolsProtocolVersion

// cursed 1.7 -> 1.8 from https://github.com/Gerrygames/ClientViaVersion
// + https://github.com/creeper123123321/ViaRewind/tree/17to18

val sharewareVersion = AprilFoolsProtocolVersion.s3d_shareware
fun registerAspirinProtocols() {
    // todo fix version checks for shareware
    Via.getManager().protocolManager.maxPathDeltaIncrease = -1 // shareware id is weird
    Via.getManager().protocolManager.registerProtocol(Protocol1_8To1_7_6, ProtocolVersion.v1_8, ProtocolVersion.v1_7_6)
}
