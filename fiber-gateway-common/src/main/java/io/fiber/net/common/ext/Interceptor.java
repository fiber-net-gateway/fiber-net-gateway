package io.fiber.net.common.ext;

public interface Interceptor<E> {
    interface Invocation<E> {
        void invoke(String project, E exchange) throws Exception;
    }

    void intercept(String project, E exchange, Invocation<E> invocation) throws Exception;
}
