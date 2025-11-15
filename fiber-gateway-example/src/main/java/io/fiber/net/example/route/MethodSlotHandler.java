package io.fiber.net.example.route;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.proxy.route.RouteConflictException;

public class MethodSlotHandler {
    private final String urlPattern;
    private final ScriptHandler[] handlers;

    public MethodSlotHandler(String urlPattern) {
        this.urlPattern = urlPattern;
        handlers = new ScriptHandler[Constant.METHODS.length + 1];
    }

    public void addHandler(HttpMethod method, ScriptHandler handler) {
        int idx = method != null ? method.ordinal() : Constant.METHODS.length;
        ScriptHandler old = handlers[idx];
        if (old != null) {
            throw new RouteConflictException("method handler already exists:" + method);
        }
        handlers[idx] = handler;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public ScriptHandler getHandler(HttpMethod method) {
        ScriptHandler handler = handlers[method.ordinal()];
        if (handler == null) {
            handler = handlers[Constant.METHODS.length];
        }
        return handler;
    }
}
