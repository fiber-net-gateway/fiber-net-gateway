package io.fiber.net.example;

import io.fiber.net.common.RouterHandler;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.server.HttpExchange;
import io.fiber.net.support.RateLimiter;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.netty4.NettyAllocatorMetrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufAllocatorMetricProvider;
import io.netty.buffer.Unpooled;
import io.prometheus.client.exporter.common.TextFormat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

class MetricRouteHandler implements RouterHandler<HttpExchange> {
    private final PrometheusMeterRegistry registry;
    private final RateLimiter rateLimiter = RateLimiter.of("echo rate",
            Duration.ofSeconds(3),
            3
    );
    private final JvmGcMetrics jvmGcMetrics = new JvmGcMetrics();


    MetricRouteHandler(PrometheusMeterRegistry registry) {
        this.registry = registry;
        new JvmMemoryMetrics().bindTo(registry);
        jvmGcMetrics.bindTo(registry);
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;
        if (allocator instanceof ByteBufAllocatorMetricProvider) {
            new NettyAllocatorMetrics((ByteBufAllocatorMetricProvider) allocator).bindTo(registry);
        }
        new JvmInfoMetrics().bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
    }

    @Override
    public String getRouterName() {
        return "metric";
    }

    @Override
    public void invoke(HttpExchange exchange) {
        exchange.discardReqBody();
        if (rateLimiter.acquirePermission()) {
            String string = registry.scrape(TextFormat.CONTENT_TYPE_004);
            exchange.setResponseHeader(Constant.CONTENT_TYPE_HEADER, TextFormat.CONTENT_TYPE_004);
            exchange.writeRawBytes(200, Unpooled.wrappedBuffer(string.getBytes(StandardCharsets.UTF_8)));
        } else {
            exchange.setResponseHeader(Constant.CONTENT_TYPE_HEADER, "text/html");
            exchange.writeRawBytes(503, Unpooled.wrappedBuffer("<h1 style=\"color: red;\">BLOCKED</h1>".getBytes()));
        }
    }

    @Override
    public void destroy() {
        jvmGcMetrics.close();
    }
}