package io.fiber.net.common.ext;

public interface Interceptor<E> {
    interface Invocation<E> {
        void invoke(RouterHandler<E> handler, E exchange) throws Exception;
    }

    void intercept(RouterHandler<E> handler, E exchange, Invocation<E> invocation) throws Exception;
}
