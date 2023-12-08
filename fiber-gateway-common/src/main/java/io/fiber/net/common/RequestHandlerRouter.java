package io.fiber.net.common;

public interface RequestHandlerRouter {
    String getRouterName();

    void invoke(HttpExchange httpExchange) throws Exception;

    void destroy();
}
