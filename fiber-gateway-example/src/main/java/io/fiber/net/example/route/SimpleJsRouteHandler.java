package io.fiber.net.example.route;

import io.fiber.net.common.ext.RouterHandler;
import io.fiber.net.server.HttpExchange;

public class SimpleJsRouteHandler implements RouterHandler<HttpExchange> {
    private final SimpleScriptHandler handler;

    public SimpleJsRouteHandler(SimpleScriptHandler handler) {
        this.handler = handler;
    }

    @Override
    public String getRouterName() {
        return handler.getRouterName();
    }

    @Override
    public void invoke(HttpExchange exchange) throws Exception {
        handler.invoke(exchange);
    }

    @Override
    public void destroy() {
        handler.destroy();
    }
}
