package com.github.creeper123123321.viaaas

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import us.myles.ViaVersion.api.PacketWrapper
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.protocol.ProtocolRegistry
import us.myles.ViaVersion.api.protocol.ProtocolVersion
import us.myles.ViaVersion.api.protocol.SimpleProtocol
import us.myles.ViaVersion.api.remapper.PacketRemapper
import us.myles.ViaVersion.api.type.Type
import us.myles.ViaVersion.packets.State
import java.net.InetAddress
import java.net.InetSocketAddress

class CloudHandlerProtocol : SimpleProtocol() {
    override fun registerPackets() {
        this.registerIncoming(State.HANDSHAKE, 0, 0, object : PacketRemapper() {
            override fun registerMap() {
                handler { wrapper: PacketWrapper ->
                    val protVer = wrapper.passthrough(Type.VAR_INT)
                    val addr = wrapper.passthrough(Type.STRING) // Server Address
                    val svPort = wrapper.passthrough(Type.UNSIGNED_SHORT)
                    val nextState = wrapper.passthrough(Type.VAR_INT)

                    val addrParts = addr.split(0.toChar())[0].split(".")
                    var foundDomain = false
                    var foundOptions = false
                    var port = 25565
                    var online = true // todo implement this between proxy and player
                    var backProtocol = 47 // todo auto protocol
                    var backAddr = ""
                    addrParts.reversed().forEach {
                        if (foundDomain) {
                            if (!foundOptions) {
                                if (it.startsWith("_")) {
                                    val arg = it.substring(2)
                                    when {
                                        it.startsWith("_p", ignoreCase = true) -> port = arg.toInt()
                                        it.startsWith("_o", ignoreCase = true) -> online = arg.toBoolean()
                                        it.startsWith("_v", ignoreCase = true) -> {
                                            try {
                                                backProtocol = Integer.parseInt(arg)
                                            } catch (e: NumberFormatException) {
                                                val closest = ProtocolVersion.getClosest(arg.replace("_", "."))
                                                if (closest != null) {
                                                    backProtocol = closest.id
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    foundOptions = true
                                }
                            }
                            if (foundOptions) {
                                backAddr = "$it.$backAddr"
                            }
                        } else if (it.equals("viaaas", ignoreCase = true)) {
                            foundDomain = true
                        }
                    }
                    backAddr = backAddr.replace(Regex("\\.$"), "")

                    println("connecting ${wrapper.user().channel!!.remoteAddress()} ($protVer) to $backAddr:$port ($backProtocol)")

                    wrapper.user().channel!!.setAutoRead(false)
                    wrapper.user().put(CloudData(
                            backendVer = backProtocol,
                            userConnection = wrapper.user(),
                            frontOnline = online
                    ))

                    Via.getPlatform().runAsync {
                        val frontForwarder = wrapper.user().channel!!.pipeline().get(CloudSideForwarder::class.java)
                        try {
                            val socketAddr = InetSocketAddress(InetAddress.getByName(backAddr), svPort)
                            val addrInfo = socketAddr.address
                            if (addrInfo.isSiteLocalAddress
                                    || addrInfo.isLoopbackAddress
                                    || addrInfo.isLinkLocalAddress
                                    || addrInfo.isAnyLocalAddress) throw SecurityException("Local addresses aren't allowed")
                            val bootstrap = Bootstrap().handler(BackendInit(wrapper.user()))
                                    .channel(NioSocketChannel::class.java)
                                    .group(wrapper.user().channel!!.eventLoop())
                                    .connect(socketAddr)

                            bootstrap.addListener {
                                if (it.isSuccess) {
                                    println("conected ${wrapper.user().channel?.remoteAddress()} to $socketAddr")
                                    val chann = bootstrap.channel() as SocketChannel
                                    chann.pipeline().get(CloudSideForwarder::class.java).other = wrapper.user().channel
                                    frontForwarder.other = chann
                                    val backHandshake = ByteBufAllocator.DEFAULT.buffer()
                                    try {
                                        backHandshake.writeByte(0) // Packet 0 handshake
                                        val connProto =
                                                if (ProtocolRegistry.getProtocolPath(protVer, backProtocol) != null) {
                                                    backProtocol
                                                } else protVer
                                        Type.VAR_INT.writePrimitive(backHandshake, connProto)
                                        val nullPos = addr.indexOf(0.toChar())
                                        Type.STRING.write(backHandshake, backAddr
                                                + (if (nullPos != -1) addr.substring(nullPos) else "")) // Server Address
                                        backHandshake.writeShort(port)
                                        Type.VAR_INT.writePrimitive(backHandshake, nextState)
                                        chann.writeAndFlush(backHandshake.retain())
                                    } finally {
                                        backHandshake.release()
                                    }
                                } else {
                                    wrapper.user().channel!!.eventLoop().submit {
                                        frontForwarder.disconnect("Couldn't connect: " + it.cause().toString())
                                    }
                                }

                                wrapper.user().channel!!.setAutoRead(true)
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

        this.registerOutgoing(State.LOGIN, 3, 3, object : PacketRemapper() {
            // set compression
            override fun registerMap() {
                handler {
                    val pipe = it.user().channel!!.pipeline()
                    val threshold = it.read(Type.VAR_INT)
                    it.cancel()
                    it.create(3) {
                        it.write(Type.VAR_INT, threshold)
                    }.send(CloudHandlerProtocol::class.java, true, true) // needs to be sent uncompressed
                    pipe.get(CloudCompressor::class.java).threshold = threshold
                    pipe.get(CloudDecompressor::class.java).threshold = threshold

                    val backPipe = pipe.get(CloudSideForwarder::class.java).other?.pipeline()
                    backPipe?.get(CloudCompressor::class.java)?.threshold = threshold
                    backPipe?.get(CloudDecompressor::class.java)?.threshold = threshold
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

fun Channel.setAutoRead(b: Boolean) {
    this.config().isAutoRead = b
    if (b) this.read()
}
