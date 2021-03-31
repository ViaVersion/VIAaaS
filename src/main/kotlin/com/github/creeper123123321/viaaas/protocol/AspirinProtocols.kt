package com.github.creeper123123321.viaaas.protocol

import com.github.creeper123123321.viaaas.protocol.id47toid5.Protocol1_8To1_7_6
import com.github.creeper123123321.viaaas.protocol.id5toid4.Protocol1_7_6to1_7_2
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.protocol.ProtocolVersion

// cursed 1.7 -> 1.8 from https://github.com/Gerrygames/ClientViaVersion
// + https://github.com/creeper123123321/ViaRewind/tree/17to18
fun registerAspirinProtocols() {
    Via.getManager().protocolManager.registerProtocol(Protocol1_7_6to1_7_2, ProtocolVersion.v1_7_6, ProtocolVersion.v1_7_1)
    Via.getManager().protocolManager.registerProtocol(Protocol1_8To1_7_6, ProtocolVersion.v1_8, ProtocolVersion.v1_7_6)
}
