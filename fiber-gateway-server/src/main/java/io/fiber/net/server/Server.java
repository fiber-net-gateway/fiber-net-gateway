package io.fiber.net.server;

import io.fiber.net.common.utils.EpollAvailable;
import io.fiber.net.common.utils.StringUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.util.AsciiString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.net.SocketAddress;
import java.text.DecimalFormat;

public class Server implements HttpServer {
    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private ServerBootstrap bootstrap;
    private Channel listenCh;
    private final EventLoopGroup eventLoopGroup;

    public Server(EventLoopGroup executors) {
        eventLoopGroup = executors;
    }

    @Override
    public void start(ServerConfig config, HttpEngine engine) throws Exception {
        bootstrap = new ServerBootstrap();
        bootstrap.channel(EpollAvailable.serverSocketClazz());
        bootstrap.group(EpollAvailable.bossGroup(), eventLoopGroup);
        ServerBootstrap option = bootstrap.option(ChannelOption.SO_BACKLOG, config.getBacklog());
        EpollAvailable.setEpollReusePort(config.isTcpReusePort(), option);
        if (config.isTcpReuseAddr()) {
            option.option(ChannelOption.SO_REUSEADDR, true);
        }
        if (config.isTcpKeepAlive()) {
            option.childOption(ChannelOption.SO_KEEPALIVE, true);
        }
        if (config.isTcpNoDelay()) {
            option.childOption(ChannelOption.TCP_NODELAY, true);
        }


        AsciiString echoServer = StringUtils.isNotEmpty(config.getEchoServer()) ? AsciiString.cached(config.getEchoServer())
                : ReqHandler.X_POWERED_BY_VALUE;

        bootstrap.childHandler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) {
                HttpServerCodec serverCodec;
                HttpServerKeepAliveHandler handler;
                ch.pipeline().addLast(serverCodec = new HttpServerCodec(config.getMaxInitialLineLength(),
                                config.getMaxHeaderSize(), config.getMaxChunkSize()))
                        .addLast(handler = new HttpServerKeepAliveHandler())
                        .addLast(new ReqHandler(config.getMaxBodySize(), engine, echoServer, serverCodec, handler));
            }
        });
        String bindIp = config.getBindIp();
        if (StringUtils.isEmpty(bindIp)) {
            bootstrap.localAddress(config.getServerPort());
        } else {
            bootstrap.localAddress(bindIp, config.getServerPort());
        }
        ChannelFuture future = bootstrap.bind().syncUninterruptibly();
        if (!future.isSuccess()) {
            throw new Exception("bind error", future.cause());
        }
        listenCh = future.channel();
        log.info("netty server({}) started at {}s", listenCh.localAddress(),
                new DecimalFormat("#.000").format((
                        System.currentTimeMillis() -
                                ManagementFactory.getRuntimeMXBean().getStartTime()
                ) / 1000.0));
    }

    @Override
    public void awaitShutdown() {
        bootstrap.config().group().terminationFuture().awaitUninterruptibly();
        bootstrap.config().childGroup().terminationFuture().awaitUninterruptibly();
    }

    @Override
    public void destroy() {
        if (listenCh != null) {
            SocketAddress arg = listenCh.localAddress();
            listenCh.close().awaitUninterruptibly();
            listenCh = null;
            bootstrap.config().group().shutdownGracefully().awaitUninterruptibly();
            log.info("netty server({}) stopped", arg);
        }
    }

}
