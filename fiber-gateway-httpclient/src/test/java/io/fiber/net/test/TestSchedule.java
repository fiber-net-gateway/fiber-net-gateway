package io.fiber.net.test;

import io.fiber.net.common.async.EngineScheduler;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.DefaultHttpClient;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class TestSchedule {
    EngineScheduler scheduler;
    NioEventLoopGroup group;
    DefaultHttpClient client;

    @Before
    public void init() throws Exception {
        scheduler = EngineScheduler.init();
        group = new NioEventLoopGroup();
        client = new DefaultHttpClient(group);
        client.getStartPromise().get();
    }

    @Test
    public void t1() {
        System.out.println(Thread.currentThread());
        Scheduler current = Scheduler.current();
        ClientExchange exchange = client.refer("www.baidu.com", 443);
        exchange.sendForResp()
                .subscribe((clientResponse, throwable) -> {
                    assert current.inLoop();

                    if (throwable != null) {
                        throwable.printStackTrace();
                        scheduler.shutdown();
                        return;
                    }

                    int status = clientResponse.status();
                    System.out.println("status: " + status);
                    for (String name : clientResponse.getHeaderNames()) {
                        String header = clientResponse.getHeader(name);
                        System.out.println(name + ": " + header);
                    }

                    clientResponse.readFullRespBody()
                            .map(buf -> {
                                try {
                                    return buf.toString(StandardCharsets.UTF_8);
                                } finally {
                                    buf.release();
                                }
                            })
                            .subscribe((s, throwable1) -> {
                                assert current.inLoop();
                                System.out.println();
                                System.out.println(s);
                                if (throwable1 != null) {
                                    throwable1.printStackTrace();
                                }
                                scheduler.shutdown();
                            });
                });
        scheduler.runLoop();
        System.out.println("=================");
    }

    @After
    public void destroy() {
        group.shutdownGracefully();
    }
}
