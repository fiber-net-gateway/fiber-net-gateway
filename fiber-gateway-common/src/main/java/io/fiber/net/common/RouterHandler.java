package io.fiber.net.common;


public interface RouterHandler<E> {
    String getRouterName();

    void invoke(E exchange) throws Exception;

    void destroy();
}
