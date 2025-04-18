package io.fiber.net.example;

import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.Disposable;
import io.fiber.net.common.async.Observable;
import io.fiber.net.common.ext.Interceptor;
import io.fiber.net.server.HttpExchange;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.netty.buffer.ByteBuf;

import java.util.concurrent.TimeUnit;

public class StatisticInterceptor implements Interceptor<HttpExchange>, HttpExchange.Listener {
    private static final HttpExchange.Attr<Recorder> TIMER = HttpExchange.createAttr();


    private final PrometheusMeterRegistry registry;

    public StatisticInterceptor(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void intercept(String project, HttpExchange exchange, Invocation<HttpExchange> invocation) throws Exception {
        Recorder recorder = Recorder.of(project, exchange.getPath(), exchange.getRequestMethod());
        TIMER.set(exchange, recorder);
        exchange.addListener(this);
        exchange.peekBody().subscribe(recorder);
        invocation.invoke(project, exchange);
    }

    @Override
    public void onBodySent(HttpExchange exchange, Throwable err) {
        Recorder recorder = TIMER.get(exchange);
        if (recorder != null) {
            int s = exchange.getWroteStatus();
            if (err != null) {
                if (err instanceof FiberException) {
                    s = ((FiberException) err).getCode();
                } else {
                    s = 500;
                }
            }
            recorder.record(registry, s);
        }
    }

    @Override
    public void onHeaderSend(HttpExchange exchange, int status) {

    }

    private static class Recorder implements Observable.Observer<ByteBuf> {
        private final long startTime = System.nanoTime();
        private final Timer.Builder builder;
        private final String project;
        private final String url;
        private final HttpMethod method;
        private final DistributionSummary.Builder bodySizeBuilder;
        private int bodyBytes;

        static Recorder of(String project, String url, HttpMethod method) {
            return new Recorder(project, url, method);
        }

        Recorder(String project, String url, HttpMethod method) {
            this.project = project;
            this.url = url;
            this.method = method;
            builder = Timer.builder("fiber_net_server_requests");
            bodySizeBuilder = DistributionSummary.builder("fiber_net_request_body_size");
        }

        public void record(MeterRegistry registry, int status) {
            builder.tag("project", project)
                    .tag("url", url)
                    .tag("method", method.name())
                    .tag("status", String.valueOf(status))
                    .register(registry)
                    .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            bodySizeBuilder.tag("project", project)
                    .tag("url", url)
                    .tag("method", method.name())
                    .register(registry).record(bodyBytes);
        }

        @Override
        public void onSubscribe(Disposable d) {

        }

        @Override
        public void onNext(ByteBuf byteBuf) {
            bodyBytes += byteBuf.readableBytes();
            byteBuf.release();
        }

        @Override
        public void onError(Throwable e) {
            bodySizeBuilder.tag("abort", "true");
        }

        @Override
        public void onComplete() {
            bodySizeBuilder.tag("abort", "false");
        }
    }
}
