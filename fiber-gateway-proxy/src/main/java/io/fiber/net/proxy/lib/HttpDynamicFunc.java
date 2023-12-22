package io.fiber.net.proxy.lib;

import io.fiber.net.common.HttpExchange;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;

public interface HttpDynamicFunc extends Library.Function {

    @Override
    default boolean isConstExpr() {
        return false;
    }

    static HttpExchange httpExchange(ExecutionContext context) {
        return (HttpExchange) context.attach();
    }
}