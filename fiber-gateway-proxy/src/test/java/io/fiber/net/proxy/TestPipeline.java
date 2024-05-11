package io.fiber.net.proxy;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.ext.RouterNameFetcher;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.json.IntNode;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.EpollAvailable;
import io.fiber.net.proxy.lib.ExtensiveHttpLib;
import io.fiber.net.script.Library;
import io.fiber.net.script.ast.Literal;
import io.fiber.net.server.HttpEngine;
import io.fiber.net.server.HttpServer;
import io.fiber.net.server.ServerConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class TestPipeline {
    HttpEngine engine;

    @Before
    public void t() throws Exception {

        engine = LibProxyMainModule.createEngine(
                binder -> {
                    binder.bindMultiBean(ProxyModule.class, MProxy.class);
                    binder.bind(MProxy.class, new MProxy());
                }
        );

        engine.addHandlerRouter(LibProxyMainModule.createProject(engine.getInjector(),
                RouterNameFetcher.DEF_ROUTER_NAME, "return sleep(+req.getQuery(\"t\"));"));

        try {
            HttpServer server = engine.getInjector().getInstance(HttpServer.class);
            server.start(new ServerConfig(), engine);
        } catch (Throwable e) {
            engine.getInjector().destroy();
            throw e;
        }
    }

    @Test
    public void r() throws Exception {
        EventLoopGroup group = EpollAvailable.bossGroup();
        Bootstrap bs = new Bootstrap();
        bs.group(group);
        bs.channel(EpollAvailable.tcpSocketClazz());

        RH rh = new RH();

        bs.handler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) {
                ch.pipeline().addLast(new HttpClientCodec())
                        .addLast(new HttpObjectAggregator(10 * 1024))
                        .addLast(rh);
            }
        });

        Channel channel = bs.connect("127.0.0.1", 16688).syncUninterruptibly()
                .channel();

        sendReq(channel, 3000);
        sendReq(channel, 10);
        channel.flush();

        rh.latch.await();
        channel.close().syncUninterruptibly();
        group.shutdownGracefully().syncUninterruptibly();
        System.out.println("shutdown....");
    }

    private static void sendReq(Channel channel, int t) {
        DefaultHttpHeaders entries = new DefaultHttpHeaders();
        entries.set(Constant.X_FIBER_PROJECT_HEADER, RouterNameFetcher.DEF_ROUTER_NAME);
        channel.write(new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.GET,
                "/?t=" + t,
                Unpooled.EMPTY_BUFFER,
                entries,
                EmptyHttpHeaders.INSTANCE
        ));
    }

    @After
    public void e() {
        engine.getInjector().destroy();
    }

    private static class RH extends ChannelInboundHandlerAdapter {

        final CountDownLatch latch = new CountDownLatch(2);

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpResponse) {
                FullHttpResponse response = (FullHttpResponse) msg;
                System.out.println(response.content().toString(StandardCharsets.UTF_8));
                latch.countDown();
            }
            super.channelRead(ctx, msg);
        }
    }

    private static class MProxy implements ProxyModule {

        @Override
        public void install(Binder binder) {
            binder.bindMultiBean(HttpLibConfigure.class, C.class);
            binder.bind(C.class, new C());
        }
    }

    private static class C implements HttpLibConfigure {

        @Override
        public void onInit(ExtensiveHttpLib lib) {
            lib.putAsyncFunc("sleep", context -> {
                int ms = context.noArgs() ? 0 : context.getArgVal(0).asInt(3000);
                Scheduler.current().schedule(() -> context.returnVal(IntNode.valueOf(ms)), ms);
            });
        }

        @Override
        public Library.DirectiveDef findDirectiveDef(String type, String name, List<Literal> literals) {
            return null;
        }
    }

}
