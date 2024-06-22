package io.fiber.net.proxy;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.HttpClient;
import io.fiber.net.server.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.concurrent.EventExecutor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class TestClient {
    private static final Logger log = LoggerFactory.getLogger(TestClient.class);

    static {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    HttpEngine engine;

    @Before
    public void init() throws Exception {
        engine = LibProxyMainModule.createEngine();
    }

    @After
    public void destroy() {
        engine.getInjector().destroy();
    }

    private static final int REQUESTS_SIZE = 200000;
    private static final int CONCURRENT_SIZE = 200;

    @Test
    public void t() throws Exception {
        Injector injector = engine.getInjector();
        engine.addInterceptor((project, exchange, invocation) -> {
            String size = exchange.getRequestHeader("x-body-size");
            int len = 0;
            if (StringUtils.isEmpty(size)) {
                log.error("error size");
            } else {
                try {
                    len = Integer.parseInt(size);
                } catch (RuntimeException e) {
                    log.error("error size: {}", size, e);
                }
            }
            int finalLen = len;
            exchange.writeRawBytes(200, exchange.readBodyUnsafe());
        });
        HttpClient client = injector.getInstance(HttpClient.class);
        EngineModule.EventLoopGroupHolder groupHolder = injector.getInstance(EngineModule.EventLoopGroupHolder.class);
        EventLoopGroup group = groupHolder.getGroup();

        Vector<BatchReqExecutor> vector = new Vector<>();
        int count = REQUESTS_SIZE / CONCURRENT_SIZE;
        CountDownLatch latch = new CountDownLatch(CONCURRENT_SIZE);
        CountDownLatch latch1 = new CountDownLatch(CONCURRENT_SIZE);

        int i = 0;
        while (i++ < CONCURRENT_SIZE) {
            group.next().execute(() -> {
                BatchReqExecutor batchReqExecutor = new BatchReqExecutor(client, latch, count);
                vector.add(batchReqExecutor);
                batchReqExecutor.start();
                latch1.countDown();
            });
        }

        latch1.await();
        while (!latch.await(3000, TimeUnit.MILLISECONDS)) {
            log.info("waiting end ... {}", latch.getCount());
            for (BatchReqExecutor executor : vector) {
                if (executor.currentReq < count) {
                    log.info("waiting item {}", executor.currentReq);
                }
            }
        }

        for (BatchReqExecutor executor : vector) {
            executor.print();
        }
    }

    private static class BatchReqExecutor implements Observable.Observer<ByteBuf> {
        private final HttpClient client;
        private final ByteBuf buf;
        private final CountDownLatch latch;
        private long createTime;
        private long endTime;
        private long startTime;
        private int currentReq;
        private int currentRespSize;
        private int errorReq;
        private Scheduler scheduler;
        private final int batchSize;

        private BatchReqExecutor(HttpClient client, CountDownLatch latch, int batchSize) {
            this.client = client;
            this.latch = latch;
            this.batchSize = batchSize;
            int len = 32 * 1024 + ThreadLocalRandom.current().nextInt(32 * 1024);
            buf = ByteBufAllocator.DEFAULT.buffer(len);
            buf.writeZero(len);
        }

        void start() {
            Scheduler.current().schedule(this::exec, 3000);
        }

        public void exec() {
            if (currentReq >= batchSize) {
                end();
                return;
            }

            if (currentReq == 0) {
                scheduler = Scheduler.current();
                createTime = System.currentTimeMillis();
            }
            ClientExchange exchange = client.refer("127.0.0.1", 16688);
            exchange.setReqBufFullFunc(ec -> buf.retainedSlice());
            exchange.setHeader("x-body-size", String.valueOf(buf.readableBytes()));
            exchange.sendForResp().switchToObservable(r -> {
                int status = r.status();
                if (status != 200) {
                    r.readFullRespBody(Scheduler.direct()).subscribe((byteBuf, throwable) -> {
                        if (throwable != null) {
                            log.warn("error read err response");
                        } else {
                            log.warn("error response {} => {}", status, byteBuf.toString(StandardCharsets.UTF_8));
                            byteBuf.release();
                        }

                    });
                    throw new FiberException("status is not ok:" + status, status, "IVD_CODE");
                }
                return r.readRespBody();
            }).subscribe(this);
        }

        public void end() {
            buf.release();
            endTime = System.currentTimeMillis();
            log.info("===结束 {} 完成请求：{}。失败：{}===",
                    endTime - createTime, currentReq, errorReq);
            latch.countDown();

        }

        private void print() {
            log.info("========用时 {} 完成请求：{}。失败：{}==========================",
                    endTime - createTime, currentReq, errorReq);
        }

        @Override
        public void onSubscribe(Disposable d) {
            assert scheduler.inLoop();
            if (startTime != 0) {
                log.error("通知错乱。。。。，onSubscribe 多次");
            }
            startTime = System.currentTimeMillis();
            currentReq++;
            currentRespSize = 0;
        }

        @Override
        public void onNext(ByteBuf byteBuf) {
            assert scheduler.inLoop();
            currentRespSize += byteBuf.readableBytes();
            byteBuf.release();
        }

        @Override
        public void onError(Throwable e) {
            assert scheduler.inLoop();
            log.error("error in request {} -> {} | {}", currentReq, currentRespSize, System.currentTimeMillis() - startTime, e);
            errorReq++;
            startTime = 0;
            exec();
        }

        @Override
        public void onComplete() {
            assert scheduler.inLoop();
//            log.info("request onComplete {} -> {} | {}", currentReq, currentRespSize, System.currentTimeMillis() - startTime);
            if (currentRespSize != buf.readableBytes()) {
                errorReq++;
                log.warn("请求返回不对：{} -> {}/{}", currentReq, currentRespSize, buf.readableBytes());
            }
            startTime = 0;
            exec();
        }
    }


    private static final int HTML_BATCH = 100;
    static final String x = "X";

    @Test
    public void t2() throws Exception {

        Injector injector = engine.getInjector();
        engine.addHandlerRouter(new RouterHandler<HttpExchange>() {
            @Override
            public String getRouterName() {
                return x;
            }

            @Override
            public void invoke(HttpExchange exchange) throws Exception {
                ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer(128646);
                buffer.writeZero(128646);
                exchange.writeRawBytes(200, buffer);
            }

            @Override
            public void destroy() {

            }
        });
        HttpClient client = injector.getInstance(HttpClient.class);
        EngineModule.EventLoopGroupHolder groupHolder = injector.getInstance(EngineModule.EventLoopGroupHolder.class);
        EventLoopGroup group = groupHolder.getGroup();
        int s = 0;
        for (EventExecutor executor : group) {
            s++;
        }
        CountDownLatch latch = new CountDownLatch(s);

        Vector<HtmlBatch> vector = new Vector<>();

        for (EventExecutor eventExecutor : groupHolder.getGroup()) {
            eventExecutor.execute(() -> {
                HtmlBatch htmlBatch = new HtmlBatch(client, latch);
                htmlBatch.exec();
                vector.add(htmlBatch);
            });
        }

        while (!latch.await(3000, TimeUnit.MILLISECONDS)) {
            log.info("waiting end ... {}", latch.getCount());
            for (HtmlBatch executor : vector) {
                if (executor.requests < HTML_BATCH) {
                    log.info("waiting html item {}", executor.requests);
                }
            }
        }

        for (HtmlBatch executor : vector) {
            executor.print();
        }

    }

    private static class HtmlBatch implements Observable.Observer<ByteBuf> {
        private final HttpClient client;
        private final CountDownLatch latch;

        int requests;
        int errorReq;

        private HtmlBatch(HttpClient client, CountDownLatch latch) {
            this.client = client;
            this.latch = latch;
        }

        private void exec() {
            if (requests >= HTML_BATCH) {
                latch.countDown();
                return;
            }

            ClientExchange exchange = client.refer("127.0.0.1", 16688);
            exchange.setHeader(Constant.X_FIBER_PROJECT_HEADER, x);
            exchange.sendForResp().switchToObservable(r -> {
                int status = r.status();
                if (status != 200) {
                    r.readFullRespBody(Scheduler.direct()).subscribe((byteBuf, throwable) -> {
                        if (throwable != null) {
                            log.warn("error read err response");
                        } else {
                            log.warn("error response {} => {}", status, byteBuf.toString(StandardCharsets.UTF_8));
                            byteBuf.release();
                        }

                    });
                    throw new FiberException("status is not ok:" + status, status, "IVD_CODE");
                }
                return r.readRespBody();
            }).subscribe(this);
        }

        private void print() {
            log.info("=======完成请求：{}。失败：{}==========================",
                    requests, errorReq);
        }


        int respSize;
        long startTime;

        @Override
        public void onSubscribe(Disposable d) {
            respSize = 0;
            if (startTime != 0) {
                log.error("通知错乱。。。。，onSubscribe 多次");
            }
            startTime = System.currentTimeMillis();
            requests++;
        }

        @Override
        public void onNext(ByteBuf byteBuf) {
            respSize += byteBuf.readableBytes();
            byteBuf.release();
        }

        @Override
        public void onError(Throwable e) {
            log.error("error in request html", e);
            startTime = 0;
            errorReq++;
            exec();
        }

        @Override
        public void onComplete() {
            if (respSize == 128646) {
                log.info("end html request {} -> .{}", System.currentTimeMillis() - startTime, respSize);
            } else {
                errorReq++;
                log.warn("end html request {} -> . with error response size {}",
                        System.currentTimeMillis() - startTime, respSize);
            }
            startTime = 0;
            exec();
        }
    }

}
