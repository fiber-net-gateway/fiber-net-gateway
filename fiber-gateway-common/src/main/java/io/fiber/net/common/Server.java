package io.fiber.net.common;

import io.fiber.net.common.ioc.Injector;

public interface Server<E> {
    void start() throws Exception;

    void stop() throws Exception;

    void destroy();

    Injector getInjector();

    String getName();

    void process(E exchange);
}
