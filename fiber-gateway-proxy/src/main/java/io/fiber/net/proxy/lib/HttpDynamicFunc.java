package io.fiber.net.proxy.lib;

import io.fiber.net.server.HttpExchange;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

public interface HttpDynamicFunc extends Library.AsyncFunction {

    static HttpExchange httpExchange(ExecutionContext context) {
        return (HttpExchange) context.attach();
    }
}