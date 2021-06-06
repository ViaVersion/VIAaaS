package com.viaversion.aas.protocol

import com.viaversion.aas.protocol.id47toid5.Protocol1_8To1_7_6
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion

// cursed 1.7 -> 1.8 from https://github.com/Gerrygames/ClientViaVersion
// + https://github.com/creeper123123321/ViaRewind/tree/17to18
fun registerAspirinProtocols() {
    Via.getManager().protocolManager.registerProtocol(Protocol1_8To1_7_6, ProtocolVersion.v1_8, ProtocolVersion.v1_7_6)
}
