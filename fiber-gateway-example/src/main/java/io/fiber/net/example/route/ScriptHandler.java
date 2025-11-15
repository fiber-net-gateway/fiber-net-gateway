package io.fiber.net.example.route;

import io.fiber.net.common.FiberException;
import io.fiber.net.proxy.route.VarConfigSource;
import io.fiber.net.server.HttpExchange;

public interface ScriptHandler {
    VarConfigSource getVarConfigSource();

    int[] getVarDefinitions();

    void invoke(HttpExchange exchange) throws FiberException;

    void destroy();
}
