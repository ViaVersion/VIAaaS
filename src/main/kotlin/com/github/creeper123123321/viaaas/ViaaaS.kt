package com.github.creeper123123321.viaaas

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import us.myles.ViaVersion.ViaManager
import us.myles.ViaVersion.api.Via
import us.myles.ViaVersion.api.data.MappingDataLoader
import us.myles.ViaVersion.api.data.UserConnection


fun main(args: Array<String>) {
    Via.init(ViaManager.builder()
            .injector(CloudInjector)
            .loader(CloudLoader)
            .commandHandler(CloudCommands)
            .platform(CloudPlatform).build())

    MappingDataLoader.enableMappingsCache()

    Via.getManager().init()
    val boss = NioEventLoopGroup()
    val worker = NioEventLoopGroup()
    val future = ServerBootstrap().group(boss, worker)
            .channel(NioServerSocketChannel::class.java)
            .childHandler(ChannelInit)
            .bind(25565)

    loop@ while (true) {
        try {
            val cmd = readLine()?.trim()?.split(" ")
            when (cmd?.get(0)) {
                "stop", "end" -> break@loop
                "viaversion", "viaver" -> Via.getManager().commandHandler.onCommand(CloudConsoleSender, cmd.subList(1, cmd.size)
                        .toTypedArray())
                else -> println("unknown command")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    future.channel().close().sync()
    boss.shutdownGracefully()
    worker.shutdownGracefully()
    Via.getManager().destroy()
}

