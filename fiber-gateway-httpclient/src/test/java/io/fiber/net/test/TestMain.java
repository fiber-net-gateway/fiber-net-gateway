package io.fiber.net.test;

import io.fiber.net.common.async.Scheduler;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.DefaultHttpClient;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

public class TestMain {
    @Test
    public void t1() throws Exception {
        NioEventLoopGroup group = new NioEventLoopGroup();


        CountDownLatch latch = new CountDownLatch(1);

        DefaultHttpClient client = new DefaultHttpClient(group);
        client.getStartPromise().get();

        group.execute(() -> {
            extracted(client, latch);
        });
        latch.await();
        CountDownLatch latch2 = new CountDownLatch(1);
        group.execute(() -> extracted(client, latch2));
        latch2.await();

        group.shutdownGracefully().syncUninterruptibly();
    }

    private static void extracted(DefaultHttpClient client, CountDownLatch latch) {
        System.out.println(Thread.currentThread());
        Scheduler current = Scheduler.current();
        ClientExchange exchange = client.refer("www.baidu.com", 443);
        exchange.setPeekConn((ex, connection) -> System.out.println("connnection: " + connection));
        exchange.sendForResp()
                .subscribe((clientResponse, throwable) -> {
                    assert current.inLoop();
                    int status = clientResponse.status();
                    System.out.println("status: " + status);
                    for (String name : clientResponse.getHeaderNames()) {
                        String header = clientResponse.getHeader(name);
                        System.out.println(name + ": " + header);
                    }

                    clientResponse.readFullRespBody()
                            .map(buf -> buf.toString(StandardCharsets.UTF_8))
                            .subscribe((s, throwable1) -> {
                                assert current.inLoop();
                                System.out.println();
                                System.out.println(s);
                                if (throwable != null) {
                                    throwable.printStackTrace();
                                }
                                latch.countDown();
                            });
                });
    }

    @Test
    public void r3() {
        for (int i = 0; i < 10; i++) {
            try {
                if (i > 0) {
                    break;
                }
            } finally {
                System.out.println("123");
            }
        }

    }

}
