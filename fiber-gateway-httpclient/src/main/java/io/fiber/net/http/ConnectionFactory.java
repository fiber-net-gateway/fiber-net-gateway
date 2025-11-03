package io.fiber.net.http;

import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;

import java.net.SocketAddress;

public interface ConnectionFactory {
    EventLoopGroup getGroup();

    void connect(SocketAddress address,
                 int timeout,
                 EventLoop eventLoop,
                 ConnCallback callback);

    interface ConnCallback {
        void onChannelCreated(Channel channel, EventLoop loop) throws Throwable;

        void onConnectSuccess(Channel channel);

        void onConnectError(Throwable e);
    }
}
