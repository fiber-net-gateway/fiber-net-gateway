package io.fiber.net.common.async;

@FunctionalInterface
public interface Function<T, R> {
    R invoke(T t) throws Throwable;
}
