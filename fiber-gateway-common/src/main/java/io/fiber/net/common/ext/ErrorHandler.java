package io.fiber.net.common.ext;

public interface ErrorHandler<E> {
    void handleErr(E exchange, Throwable err);
}
