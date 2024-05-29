package io.fiber.net.example;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.ext.Interceptor;
import io.fiber.net.server.HttpExchange;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.util.concurrent.TimeUnit;

public class StatisticInterceptor implements Interceptor<HttpExchange>, HttpExchange.Listener {
    private static final HttpExchange.Attr<Recorder> TIMER = HttpExchange.createAttr();


    private final PrometheusMeterRegistry registry;

    public StatisticInterceptor(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void intercept(String project, HttpExchange exchange, Invocation<HttpExchange> invocation) throws Exception {
        TIMER.set(exchange, Recorder.of(project, exchange.getPath(), exchange.getRequestMethod()));
        exchange.addListener(this);
        invocation.invoke(project, exchange);
    }

    @Override
    public void onBodySent(HttpExchange exchange, int state) {
        Recorder recorder = TIMER.get(exchange);
        if (recorder != null) {
            recorder.record(registry, state);
        }
    }


    private static class Recorder {
        private final long startTime = System.nanoTime();
        private final Timer.Builder builder;

        static Recorder of(String project, String url, HttpMethod method) {
            Recorder recorder = new Recorder();
            recorder.builder.tag("project", project)
                    .tag("url", url)
                    .tag("method", method.name());
            return recorder;
        }

        Recorder() {
            builder = Timer.builder("fiber_net_server_requests");
        }

        public void record(MeterRegistry registry, int status) {
            builder.tag("status", String.valueOf(status))
                    .register(registry)
                    .record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        }
    }
}
