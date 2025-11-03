package io.fiber.net.server;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.ext.AbstractServer;
import io.fiber.net.common.ext.ErrorHandler;
import io.fiber.net.common.ext.Router;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.EpollAvailable;
import io.fiber.net.common.utils.ErrorInfo;
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

public class HttpServer extends AbstractServer<HttpExchange> {
    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
    private final ServerConfig config;
    private final String name;
    private ServerBootstrap bootstrap;
    private Channel listenCh;

    public HttpServer(Injector injector, String name) {
        this(injector, name, new ServerConfig());
    }

    public HttpServer(Injector injector, String name, ServerConfig config) {
        super(injector);
        this.config = config;
        this.name = name;
    }

    public HttpServer(Injector injector, Router<HttpExchange> router, ErrorHandler<HttpExchange> errorHandler, String name, ServerConfig config) {
        super(injector, router, errorHandler);
        this.config = config;
        this.name = name;
    }


    public static final ErrorHandler<HttpExchange> ERR_HANDLER = new WriteJsonErrHandler();

    @Override
    public void start() throws Exception {
        bootstrap = new ServerBootstrap();
        bootstrap.channel(EpollAvailable.serverSocketClazz());
        bootstrap.group(injector.getInstance(EventLoopGroup.class), injector.getParent().getInstance(EventLoopGroup.class));
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
                        .addLast(new ReqHandler(config.getMaxBodySize(), HttpServer.this, echoServer, serverCodec, handler));
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
        log.info("{} server({}) started at {}s", getName(), listenCh.localAddress(),
                new DecimalFormat("#.000").format((
                        System.currentTimeMillis() -
                                ManagementFactory.getRuntimeMXBean().getStartTime()
                ) / 1000.0));
    }

    @Override
    public void stop() {
        if (listenCh != null) {
            SocketAddress arg = listenCh.localAddress();
            listenCh.close().awaitUninterruptibly();
            listenCh = null;
            log.info("{} server({}) stopped", getName(), arg);
        }

        if (bootstrap != null) {
            bootstrap.config().group().shutdownGracefully().awaitUninterruptibly();
            bootstrap = null;
        }
    }

    public Channel getListenCh() {
        return listenCh;
    }

    public ServerConfig getConfig() {
        return config;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void destroy() {
        super.destroy();
        getInjector().destroy();
    }

    static class WriteJsonErrHandler implements ErrorHandler<HttpExchange> {


        @Override
        public void handleErr(HttpExchange exchange, Throwable err) {
            exchange.discardReqBody();
            if (!(err instanceof FiberException)) {
                log.error("unknown error in handler ....", err);
            }
            if (!exchange.isResponseWrote()) {
                ErrorInfo ei = ErrorInfo.of(err);
                exchange.writeJson(ei.getStatus(), ei);
            }
        }
    }
}
