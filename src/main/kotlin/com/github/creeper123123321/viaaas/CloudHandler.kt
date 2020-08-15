package com.github.creeper123123321.viaaas

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil

class CloudSideForwarder(var other: Channel?): SimpleChannelInboundHandler<ByteBuf>() {
    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        try {
            other?.write(msg)
        } finally {
            ReferenceCountUtil.release(msg)
        }
    }
}