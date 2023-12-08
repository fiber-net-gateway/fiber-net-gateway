package io.fiber.net.test;

import io.fiber.net.common.Engine;
import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpExchange;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.async.Scheduler;
import io.fiber.net.common.async.internal.Subject;
import io.fiber.net.common.ioc.Binder;
import io.fiber.net.common.ioc.Injector;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.server.EngineModule;
import io.fiber.net.server.HttpServer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ResourceLeakDetector;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class Main {
    static {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
    }

    private static class M extends EngineModule {
        @Override
        public void install(Binder binder) {
            super.install(binder);
        }
    }

    private static class Ob extends Subject<ByteBuf> implements Runnable {

        private boolean start;
        int i;

        @Override
        public void subscribe(Observer<? super ByteBuf> observer) {
            super.subscribe(observer);
            if (!start) {
                start = true;
                Scheduler.current().schedule(this, 1000);
            }
        }

        @Override
        protected ByteBuf noSubscriberMerge(ByteBuf previous, ByteBuf current) {
            throw new UnsupportedOperationException("xxxxx");
        }

        @Override
        protected void onDismissClear(ByteBuf value) {
            value.release();
        }

        @Override
        public void run() {
            onNext(Unpooled.wrappedBuffer(("adffadfa:" + i + "\r\n").getBytes()));
            if (++i > 5) {
                onComplete();
                return;
            }
            Scheduler.current().schedule(this, 3000);
        }
    }

    private static class Ob2 extends Subject<ByteBuf> implements Observable.Observer<ByteBuf> {
        final HttpExchange httpExchange;
        final Scheduler scheduler = Scheduler.current();

        private Ob2(HttpExchange httpExchange) {
            this.httpExchange = httpExchange;
        }

        @Override
        public void onSubscribe(Disposable d) {
            try {
                httpExchange.writeRawBytes(200, this);
            } catch (FiberException ignore) {
            }
        }

        @Override
        public void onNext(ByteBuf value) {
            ByteBuf buffer = ByteBufAllocator.DEFAULT.buffer();
            buffer.writeCharSequence("--->收到了：", StandardCharsets.UTF_8);
            super.onNext(buffer);
            super.onNext(value);
        }

        @Override
        public void onComplete() {
            super.onComplete();
        }

        @Override
        public void onError(Throwable error) {
            super.onError(error);
        }

        @Override
        public Scheduler scheduler() {
            return scheduler;
        }

        @Override
        protected ByteBuf noSubscriberMerge(ByteBuf previous, ByteBuf current) {
            throw new UnsupportedOperationException("xxxxx");
        }

        @Override
        protected void onDismissClear(ByteBuf value) {
            value.release();
        }
    }

    @Test
    public void main() throws Exception {
        Injector injector = Injector.getRoot().createChild(new M());
        try {
            Engine instance = injector.getInstance(Engine.class);
            instance.addInterceptor((project, httpExchange, invocation) -> {
                String path = httpExchange.getPath();
                if (path.contains("echo")) {
                    httpExchange.writeRawBytes(200, httpExchange.readReqBody());
                } else if (path.contains("async")) {
                    httpExchange.writeRawBytes(200, new Ob(), true);
                } else {
                    httpExchange.setResponseHeader(Constant.CONTENT_TYPE_HEADER, "text/plain;charset=utf-8");
                    httpExchange.readReqBody().subscribe(new Ob2(httpExchange));
                }
            });
            HttpServer server = injector.getInstance(HttpServer.class);
            server.start(instance);
            server.awaitShutdown();
        } finally {
            injector.destroy();
        }
    }
}