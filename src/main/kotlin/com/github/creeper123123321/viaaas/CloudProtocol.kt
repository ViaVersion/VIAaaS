package com.github.creeper123123321.viaaas

import io.netty.channel.Channel
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
import java.math.BigInteger
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom

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

                    val parsed = VIAaaSAddress().parse(addr, VIAaaSConfig.hostName)
                    val backPort = parsed.port ?: receivedPort
                    val backAddr = parsed.realAddress
                    val backProto = parsed.protocol ?: 47

                    wrapper.write(Type.STRING, backAddr)
                    wrapper.write(Type.UNSIGNED_SHORT, backPort)

                    val playerAddr = wrapper.user().channel!!.remoteAddress()
                    logger.info("connecting $playerAddr ($playerVer) -> $backAddr:$backPort ($backProto)")

                    wrapper.user().put(CloudData(
                            userConnection = wrapper.user(),
                            backendVer = backProto,
                            frontOnline = parsed.online,
                            altName = parsed.altUsername))

                    wrapper.passthrough(Type.VAR_INT) // Next state
                }
            }
        })
    }
}

fun Channel.setAutoRead(b: Boolean) {
    this.config().isAutoRead = b
    if (b) this.read()
}

val secureRandom = SecureRandom.getInstanceStrong()

fun twosComplementHexdigest(digest: ByteArray): String {
    return BigInteger(digest).toString(16)
}

// https://github.com/VelocityPowered/Velocity/blob/0dd6fe1ef2783fe1f9322af06c6fd218aa67cdb1/proxy/src/main/java/com/velocitypowered/proxy/util/EncryptionUtils.java
fun generateServerHash(serverId: String, sharedSecret: ByteArray?, key: PublicKey): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update(serverId.toByteArray(Charsets.ISO_8859_1))
    digest.update(sharedSecret)
    digest.update(key.encoded)
    return twosComplementHexdigest(digest.digest())
}

data class CloudData(val userConnection: UserConnection,
                     var backendVer: Int,
                     var frontOnline: Boolean,
                     var altName: String?
) : StoredObject(userConnection)