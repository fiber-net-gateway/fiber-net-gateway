package io.fiber.net.common.ext;


public interface RouterHandler<E> {
    String getRouterName();

    void invoke(E exchange) throws Exception;

    void destroy();
}
