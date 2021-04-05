package com.viaversion.aas.protocol.id5toid4

import com.viaversion.aas.protocol.INSERT_DASHES
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.SimpleProtocol
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State


// Based on https://github.com/Gerrygames/ClientViaVersion
object Protocol1_7_6to1_7_2 : SimpleProtocol() {
    override fun registerPackets() {
        //Login Success
        this.registerOutgoing(State.LOGIN, 0x02, 0x02, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.STRING, INSERT_DASHES)
            }
        })

        //Spawn Player
        this.registerOutgoing(State.PLAY, 0x0C, 0x0C, object : PacketRemapper() {
            override fun registerMap() {
                map(Type.VAR_INT)
                map(Type.STRING, INSERT_DASHES)
                map(Type.STRING)
                create { packetWrapper -> packetWrapper.write(Type.VAR_INT, 0) }
            }
        })
    }

    override fun init(userConnection: UserConnection) {}
}