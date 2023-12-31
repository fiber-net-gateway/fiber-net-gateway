package io.fiber.net.common.utils;


import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class EpollAvailable {
    private static class EpollFactory0 {

        static EventLoopGroup evGroup(int nThreads) {
            return new EpollEventLoopGroup(nThreads);
        }

        static Class<? extends ServerSocketChannel> serverSocketClazz() {
            return EpollServerSocketChannel.class;
        }

        static Class<? extends SocketChannel> tcpSocketClazz() {
            return EpollSocketChannel.class;
        }

        static Class<? extends DatagramChannel> udpSocketClazz() {
            return EpollDatagramChannel.class;
        }

        static boolean isEpollLoop(EventLoopGroup group) {
            return group instanceof EpollEventLoopGroup;
        }
    }

    private static final boolean EPOLL_AV;

    static {
        boolean ea;
        try {
            ea = Epoll.isAvailable();
        } catch (Throwable e) {
            ea = false;
        }
        EPOLL_AV = ea;
    }

    public static boolean isEpollAv() {
        return EPOLL_AV;
    }

    public static EventLoopGroup bossGroup() {
        if (EPOLL_AV) {
            return EpollFactory0.evGroup(1);
        }
        return new NioEventLoopGroup(1);
    }

    public static EventLoopGroup workerGroup() {
        if (EPOLL_AV) {
            return EpollFactory0.evGroup(0);
        }
        return new NioEventLoopGroup(0);
    }

    public static Class<? extends ServerSocketChannel> serverSocketClazz() {
        if (EPOLL_AV) {
            return EpollFactory0.serverSocketClazz();
        }
        return NioServerSocketChannel.class;
    }

    public static Class<? extends SocketChannel> tcpSocketClazz() {
        if (EPOLL_AV) {
            return EpollFactory0.tcpSocketClazz();
        }
        return NioSocketChannel.class;
    }

    public static Class<? extends DatagramChannel> dupSocketClazz() {
        if (EPOLL_AV) {
            return EpollFactory0.udpSocketClazz();
        }
        return NioDatagramChannel.class;
    }

    public static boolean isEpollLoop(EventLoopGroup group) {
        if (EPOLL_AV) {
            return EpollFactory0.isEpollLoop(group);
        }
        return false;
    }
}