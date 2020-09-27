package com.github.creeper123123321.viaaas

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.ChannelOption
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.data.UserConnection
import us.myles.ViaVersion.api.protocol.Protocol
import us.myles.ViaVersion.api.protocol.ProtocolPipeline
import us.myles.ViaVersion.api.protocol.ProtocolRegistry
import us.myles.ViaVersion.api.protocol.SimpleProtocol
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.logging.Logger
import javax.naming.NameNotFoundException
import javax.naming.directory.InitialDirContext


class CloudPipeline(userConnection: UserConnection) : ProtocolPipeline(userConnection) {
    override fun registerPackets() {
        super.registerPackets()
        add(CloudHeadProtocol)
        add(CloudTailProtocol)
    }

    override fun add(protocol: Protocol<*, *, *, *>?) {
        super.add(protocol)
        pipes().remove(CloudHeadProtocol)
        pipes().add(0, CloudHeadProtocol)
        pipes().remove(CloudTailProtocol)
        pipes().add(CloudTailProtocol)
    }
}

object CloudHeadProtocol : SimpleProtocol() {
    val logger = Logger.getLogger("CloudHandlerProtocol")
    override fun registerPackets() {
        this.registerIncoming(State.HANDSHAKE, 0, 0, object : PacketRemapper() {
            override fun registerMap() {
                handler { wrapper: PacketWrapper ->
                    val playerVer = wrapper.passthrough(Type.VAR_INT)
                    val addr = wrapper.passthrough(Type.STRING) // Server Address
                    wrapper.passthrough(Type.UNSIGNED_SHORT)
                    val nextState = wrapper.passthrough(Type.VAR_INT)

                    val parsed = ViaaaSAddress().parse(addr)

                    logger.info("connecting ${wrapper.user().channel!!.remoteAddress()} ($playerVer) to ${parsed.realAddress}:${parsed.port} (${parsed.protocol})")

                    wrapper.user().channel!!.setAutoRead(false)
                    wrapper.user().put(CloudData(
                            backendVer = parsed.protocol,
                            userConnection = wrapper.user(),
                            frontOnline = parsed.online
                    ))

                    Via.getPlatform().runAsync {
                        val frontForwarder = wrapper.user().channel!!.pipeline().get(CloudSideForwarder::class.java)
                        try {
                            var srvResolvedAddr = parsed.realAddress
                            var srvResolvedPort = parsed.port
                            if (srvResolvedPort == 25565) {
                                try {
                                    // https://github.com/GeyserMC/Geyser/blob/99e72f35b308542cf0dbfb5b58816503c3d6a129/connector/src/main/java/org/geysermc/connector/GeyserConnector.java
                                    val ctx = InitialDirContext()
                                    val attr = ctx.getAttributes("dns:///_minecraft._tcp.${parsed.realAddress}", arrayOf("SRV"))["SRV"]
                                    if (attr != null && attr.size() > 0) {
                                        val record = (attr.get(0) as String).split(" ").toTypedArray()
                                        srvResolvedAddr = record[3]
                                        srvResolvedPort = record[2].toInt()
                                    }
                                } catch (ignored: NameNotFoundException) {
                                }
                            }
                            val socketAddr = InetSocketAddress(InetAddress.getByName(srvResolvedAddr), srvResolvedPort)
                            val addrInfo = socketAddr.address
                            if (addrInfo.isSiteLocalAddress
                                    || addrInfo.isLoopbackAddress
                                    || addrInfo.isLinkLocalAddress
                                    || addrInfo.isAnyLocalAddress) throw SecurityException("Local addresses aren't allowed")
                            val bootstrap = Bootstrap().handler(BackendInit(wrapper.user()))
                                    .channel(NioSocketChannel::class.java)
                                    .group(wrapper.user().channel!!.eventLoop())
                                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 15_000) // Half of mc timeout
                                    .connect(socketAddr)

                            bootstrap.addListener {
                                if (it.isSuccess) {
                                    logger.info("conected ${wrapper.user().channel?.remoteAddress()} to $socketAddr")
                                    val chann = bootstrap.channel() as SocketChannel
                                    chann.pipeline().get(CloudSideForwarder::class.java).other = wrapper.user().channel
                                    frontForwarder.other = chann
                                    val backHandshake = ByteBufAllocator.DEFAULT.buffer()
                                    try {
                                        val nullParts = addr.split(0.toChar())
                                        backHandshake.writeByte(0) // Packet 0 handshake
                                        val connProto = if (ProtocolRegistry.getProtocolPath(playerVer, parsed.protocol) != null) parsed.protocol else playerVer
                                        Type.VAR_INT.writePrimitive(backHandshake, connProto)
                                        Type.STRING.write(backHandshake, srvResolvedAddr + (if (nullParts.size == 2) 0.toChar() + nullParts[1] else "")) // Server Address
                                        backHandshake.writeShort(srvResolvedPort)
                                        Type.VAR_INT.writePrimitive(backHandshake, nextState)
                                        chann.writeAndFlush(backHandshake.retain())
                                    } finally {
                                        backHandshake.release()
                                    }
                                    wrapper.user().channel!!.setAutoRead(true)
                                } else {
                                    wrapper.user().channel!!.eventLoop().submit {
                                        frontForwarder.disconnect("Couldn't connect: " + it.cause().toString())
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            wrapper.user().channel!!.eventLoop().submit {
                                frontForwarder.disconnect("Couldn't connect: $e")
                            }
                        }
                    }
                }
            }
        })

        this.registerOutgoing(State.LOGIN, 1, 1, object : PacketRemapper() {
            // encryption request
            override fun registerMap() {
                handler {
                    val frontForwarder = it.user().channel!!.pipeline().get(CloudSideForwarder::class.java)
                    it.cancel()
                    frontForwarder.disconnect("Online mode in backend currently isn't compatible")
                }
            }
        })
    }
}

object CloudTailProtocol : SimpleProtocol() {
    override fun registerPackets() {
        this.registerOutgoing(State.LOGIN, 3, 3, object : PacketRemapper() {
            // set compression
            override fun registerMap() {
                handler {
                    val pipe = it.user().channel!!.pipeline()
                    val threshold = it.read(Type.VAR_INT)
                    it.cancel()
                    try {
                        it.create(3) {
                            it.write(Type.VAR_INT, threshold)
                        }.send(CloudTailProtocol::class.java, true, true) // needs to be sent uncompressed
                    } catch (ignored: Exception) {
                        // ViaRewind cancels it
                    }
                    pipe.get(CloudCompressor::class.java).threshold = threshold
                    pipe.get(CloudDecompressor::class.java).threshold = threshold

                    val backPipe = pipe.get(CloudSideForwarder::class.java).other!!.pipeline()
                    backPipe.get(CloudCompressor::class.java)?.threshold = threshold
                    backPipe.get(CloudDecompressor::class.java)?.threshold = threshold
                }
            }
        })
    }
}

fun Channel.setAutoRead(b: Boolean) {
    this.config().isAutoRead = b
    if (b) this.read()
}
