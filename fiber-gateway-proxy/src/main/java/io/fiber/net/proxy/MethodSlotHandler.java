package io.fiber.net.proxy;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.ext.RouterHandler;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.server.HttpExchange;

public class MethodSlotHandler {
    private final String urlPattern;
    private final RouterHandler<HttpExchange>[] handlers;

    @SuppressWarnings("unchecked")
    public MethodSlotHandler(String urlPattern) {
        this.urlPattern = urlPattern;
        handlers = new RouterHandler[Constant.METHODS.length + 1];
    }

    public void addHandler(HttpMethod method, RouterHandler<HttpExchange> handler) {
        int idx = method != null ? method.ordinal() : Constant.METHODS.length;
        RouterHandler<HttpExchange> old = handlers[idx];
        if (old != null) {
            throw new RouteConflictException("method handler already exists:" + method);
        }
        handlers[idx] = handler;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public RouterHandler<HttpExchange> getHandler(HttpMethod method) {
        RouterHandler<HttpExchange> handler = handlers[method.ordinal()];
        if (handler == null) {
            handler = handlers[Constant.METHODS.length];
        }
        return handler;
    }
}
