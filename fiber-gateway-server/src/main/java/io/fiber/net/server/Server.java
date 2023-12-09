package io.fiber.net.server;

import io.fiber.net.common.Engine;
import io.fiber.net.common.utils.EpollAvailable;
import io.fiber.net.common.utils.StringUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;

public class Server implements HttpServer {
    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private ServerBootstrap bootstrap;
    private Channel listenCh;
    private final EventLoopGroup eventLoopGroup;

    public Server(EventLoopGroup executors) {
        eventLoopGroup = executors;
    }

    @Override
    public void start(ServerConfig config, Engine engine) {
        bootstrap = new ServerBootstrap();
        bootstrap.channel(EpollAvailable.serverSocketClazz());
        bootstrap.group(EpollAvailable.bossGroup(), eventLoopGroup);
        ServerBootstrap option = bootstrap.option(ChannelOption.SO_BACKLOG, config.getBacklog());

        if (config.isTcpKeepAlive()) {
            option.childOption(ChannelOption.SO_KEEPALIVE, true);
        }
        if (config.isTcpNoDelay()) {
            option.childOption(ChannelOption.TCP_NODELAY, true);
        }

        bootstrap.childHandler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new HttpServerCodec(config.getMaxInitialLineLength(),
                                config.getMaxHeaderSize(), config.getMaxChunkSize()))
                        .addLast(new HttpServerKeepAliveHandler())
                        .addLast(new ReqHandler(config.getMaxBodySize(), engine));
            }
        });
        String bindIp = config.getBindIp();
        if (StringUtils.isEmpty(bindIp)) {
            bootstrap.localAddress(config.getServerPort());
        } else {
            bootstrap.localAddress(bindIp, config.getServerPort());
        }
        ChannelFuture future = null;
        future = bootstrap.bind().syncUninterruptibly();
        listenCh = future.channel();
        log.info("netty server({}) started", listenCh.localAddress());
    }

    @Override
    public void awaitShutdown() throws InterruptedException {
        bootstrap.config().group().terminationFuture().await();
        bootstrap.config().childGroup().terminationFuture().await();
    }

    @Override
    public void destroy() {
        SocketAddress arg = null;
        if (listenCh != null) {
            arg = listenCh.localAddress();
            listenCh.close().awaitUninterruptibly();
            listenCh = null;
        }
        bootstrap.config().group().shutdownGracefully().awaitUninterruptibly();
        log.info("netty server({}) stopped", arg);
    }

}
