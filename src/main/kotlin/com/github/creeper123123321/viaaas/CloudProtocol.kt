package com.github.creeper123123321.viaaas

import org.slf4j.LoggerFactory
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.data.StoredObject
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.Protocol
import us.myles.ViaVersion.api.protocol.ProtocolPipeline
import us.myles.ViaVersion.api.protocol.SimpleProtocol
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State

class CloudPipeline(userConnection: UserConnection) : ProtocolPipeline(userConnection) {
    override fun registerPackets() {
        super.registerPackets()
        add(CloudHeadProtocol) // add() will add tail protocol
    }

    override fun add(protocol: Protocol<*, *, *, *>?) {
        super.add(protocol)
        pipes().removeIf { it == CloudHeadProtocol }
        pipes().add(0, CloudHeadProtocol)
    }
}

object CloudHeadProtocol : SimpleProtocol() {
    val logger = LoggerFactory.getLogger("CloudHandlerProtocol")
    override fun registerPackets() {
        this.registerIncoming(State.HANDSHAKE, 0, 0, object : PacketRemapper() {
            override fun registerMap() {
                handler { wrapper: PacketWrapper ->
                    val playerVer = wrapper.passthrough(Type.VAR_INT)
                    val addr = wrapper.read(Type.STRING) // Server Address
                    val receivedPort = wrapper.read(Type.UNSIGNED_SHORT)

                    val parsed = VIAaaSAddress().parse(addr.substringBefore(0.toChar()), VIAaaSConfig.hostName)
                    val backPort = parsed.port ?: if (VIAaaSConfig.defaultBackendPort == -1) {
                        receivedPort
                    } else {
                        VIAaaSConfig.defaultBackendPort
                    }
                    val backAddr = parsed.realAddress
                    val backProto = parsed.protocol ?: 47

                    wrapper.write(Type.STRING, backAddr)
                    wrapper.write(Type.UNSIGNED_SHORT, backPort)

                    val playerAddr = wrapper.user().channel!!.pipeline()
                        .get(CloudMinecraftHandler::class.java)!!.address
                    logger.info("Connecting $playerAddr ($playerVer) -> $backAddr:$backPort ($backProto)")

                    wrapper.user().put(
                        CloudData(
                            userConnection = wrapper.user(),
                            backendVer = backProto,
                            frontOnline = parsed.online,
                            altName = parsed.altUsername,
                            hadHostname = parsed.viaSuffix == null
                        )
                    )

                    wrapper.passthrough(Type.VAR_INT) // Next state
                }
            }
        })
    }
}

data class CloudData(
    val userConnection: UserConnection,
    var backendVer: Int,
    var frontOnline: Boolean,
    var altName: String?,
    var hadHostname: Boolean
) : StoredObject(userConnection)