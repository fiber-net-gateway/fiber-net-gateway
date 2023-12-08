package io.fiber.net.common.async.internal;

import io.fiber.net.common.async.Consumer;

import java.util.function.BiFunction;

public class Functions {
    private Functions() {
    }

    final static Consumer<?> NOOP_CONSUMER = a -> {
    };
    final static BiFunction<Object, Object, Object> USE_LATER_MERGER = (a, b) -> b;


    @SuppressWarnings("unchecked")
    public static <T> Consumer<T> getNoopConsumer() {
        return (Consumer<T>) NOOP_CONSUMER;
    }

    @SuppressWarnings("unchecked")
    public static <T, P, R> BiFunction<T, P, R> getUseLaterMerger() {
        return (BiFunction<T, P, R>) USE_LATER_MERGER;
    }
}
