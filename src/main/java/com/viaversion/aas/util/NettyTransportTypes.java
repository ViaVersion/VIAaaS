package com.viaversion.aas.util;

import io.netty.channel.ChannelFactory;
import io.netty.channel.IoHandlerFactory;
import io.netty.channel.epoll.*;
import io.netty.channel.kqueue.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.*;

import java.util.function.Supplier;

public enum NettyTransportTypes {
    IO_URING(IoUringIoHandler::newFactory, IoUringServerSocketChannel::new, IoUringSocketChannel::new, IoUringDatagramChannel::new),
    EPOLL(EpollIoHandler::newFactory, EpollServerSocketChannel::new, EpollSocketChannel::new, EpollDatagramChannel::new),
    KQUEUE(KQueueIoHandler::newFactory, KQueueServerSocketChannel::new, KQueueSocketChannel::new, KQueueDatagramChannel::new),
    NIO(NioIoHandler::newFactory, NioServerSocketChannel::new, NioSocketChannel::new, NioDatagramChannel::new);

    private final Supplier<IoHandlerFactory> ioHandlerFactory;
    private final ChannelFactory<ServerSocketChannel> serverChannelFactory;
    private final ChannelFactory<SocketChannel> channelFactory;
    private final ChannelFactory<DatagramChannel> datagramChannelFactory;

    NettyTransportTypes(Supplier<IoHandlerFactory> ioHandlerFactory, ChannelFactory<ServerSocketChannel> serverChannelFactory, ChannelFactory<SocketChannel> channelFactory, ChannelFactory<DatagramChannel> datagramChannelFactory) {
        this.ioHandlerFactory = ioHandlerFactory;
        this.serverChannelFactory = serverChannelFactory;
        this.channelFactory = channelFactory;
        this.datagramChannelFactory = datagramChannelFactory;
    }

    public static NettyTransportTypes getDefault() {
        if (IoUring.isAvailable()) return IO_URING;
        if (Epoll.isAvailable()) return EPOLL;
        if (KQueue.isAvailable()) return KQUEUE;
        return NIO;
    }

    public IoHandlerFactory getIoHandlerFactory() {
        return ioHandlerFactory.get();
    }

    public ChannelFactory<ServerSocketChannel> getServerChannelFactory() {
        return serverChannelFactory;
    }

    public ChannelFactory<SocketChannel> getChannelFactory() {
        return channelFactory;
    }

    public ChannelFactory<DatagramChannel> getDatagramChannelFactory() {
        return datagramChannelFactory;
    }
}
