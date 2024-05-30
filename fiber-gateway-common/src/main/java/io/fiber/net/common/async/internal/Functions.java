package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Consumer;
import io.fiber.net.common.async.Function2;

public class Functions {
    private Functions() {
    }

    final static Consumer<?> NOOP_CONSUMER = a -> {
    };
    final static Function2<Object, Object, Object> USE_LATER_MERGER = (a, b) -> b;


    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> getNoopConsumer() {
        return (Consumer<T>) NOOP_CONSUMER;
    }

    @SuppressWarnings("unchecked")
    public static <T, P, R> Function2<T, P, R> getUseLaterMerger() {
        return (Function2<T, P, R>) USE_LATER_MERGER;
    }
}
