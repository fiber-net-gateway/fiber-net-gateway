package io.fiber.net.common.ext;


import io.fiber.net.common.HttpExchange;

public interface Interceptor {
    interface Invocation {
        void invoke(String project, HttpExchange httpExchange) throws Exception;
    }

    void intercept(String project, HttpExchange httpExchange, Invocation invocation) throws Exception;
}
