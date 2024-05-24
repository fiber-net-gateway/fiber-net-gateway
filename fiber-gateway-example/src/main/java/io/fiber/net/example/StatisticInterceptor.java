package io.fiber.net.example;

import io.fiber.net.common.ext.Interceptor;
import io.fiber.net.server.HttpExchange;
import io.fiber.net.support.RateLimiter;
import io.netty.buffer.Unpooled;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class StatisticInterceptor implements Interceptor<HttpExchange>, HttpExchange.Listener {

    private final Map<Integer, LongAdder> longAdderMap = new ConcurrentHashMap<>();
    private final RateLimiter rateLimiter = RateLimiter.of("echo rate",
            Duration.ofSeconds(3),
            3
    );

    @Override
    public void intercept(String project, HttpExchange exchange, Invocation<HttpExchange> invocation) throws Exception {
        if ("echo".equals(project)) {
            exchange.discardReqBody();
            if (rateLimiter.acquirePermission()) {
                Map<Integer, Long> value = new HashMap<>();
                for (Map.Entry<Integer, LongAdder> entry : longAdderMap.entrySet()) {
                    value.put(entry.getKey(), entry.getValue().longValue());
                }
                exchange.writeRawBytes(200, Unpooled.wrappedBuffer(value.toString().getBytes()));
            } else {
                exchange.writeRawBytes(503, Unpooled.wrappedBuffer("BLOCK".getBytes()));
            }
            return;
        }
        exchange.addListener(this);
        invocation.invoke(project, exchange);
    }

    @Override
    public void onBodySent(HttpExchange exchange, int state) {
        longAdderMap.computeIfAbsent(state, k -> new LongAdder()).increment();
    }
}
