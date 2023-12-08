package io.fiber.net.common.async;

@FunctionalInterface
public interface Function3<T, P, Q, R> {
    R invoke(T t, P p, Q q) throws Throwable;
}
