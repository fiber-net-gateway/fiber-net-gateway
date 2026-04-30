package io.fiber.net.http.impl;

import io.fiber.net.http.HttpHost;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.SslHandler;
import org.junit.Assert;
import org.junit.Test;

public class HttpConnectionHandlerTest {

    @Test
    public void shouldCreateSslHandlerWithPeerHostForSni() {
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        Channel channel = new NioSocketChannel();
        try {
            EventLoop eventLoop = group.next();
            HttpHost host = HttpHost.create("https://ark.cn-beijing.volces.com");
            channel.pipeline().addLast(new HttpConnectionHandler(channel, host, eventLoop, new PoolConfig()));

            eventLoop.register(channel).syncUninterruptibly();
            eventLoop.submit(() -> {
            }).syncUninterruptibly();

            SslHandler sslHandler = channel.pipeline().get(SslHandler.class);
            Assert.assertNotNull(channel.pipeline().names().toString(), sslHandler);
            Assert.assertEquals("ark.cn-beijing.volces.com", sslHandler.engine().getPeerHost());
            Assert.assertEquals(443, sslHandler.engine().getPeerPort());
        } finally {
            channel.close().syncUninterruptibly();
            group.shutdownGracefully().syncUninterruptibly();
        }
    }
}
