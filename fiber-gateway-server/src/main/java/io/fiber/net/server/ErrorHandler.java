package io.fiber.net.server;

public interface ErrorHandler {
    void handleErr(HttpExchange exchange, Throwable err);
}
