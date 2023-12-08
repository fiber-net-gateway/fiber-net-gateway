package io.fiber.net.common.async;

@FunctionalInterface
public interface Function2<T, P, R> {
    R invoke(T t, P p) throws Throwable;
}
