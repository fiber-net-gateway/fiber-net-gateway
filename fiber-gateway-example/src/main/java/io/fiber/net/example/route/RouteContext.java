package io.fiber.net.example.route;

import io.fiber.net.common.FiberException;
import io.fiber.net.proxy.route.RoutePathMatcher;
import io.fiber.net.server.HttpExchange;

public class RouteContext implements RoutePathMatcher.MappingContext<MethodSlotHandler> {

    private final int maxPathVarCount;
    private final HttpExchange exchange;
    String[] pathVars;
    int pathVarCount;
    int varOffset;
    private ScriptHandler handler;

    public RouteContext(int maxPathVarCount, HttpExchange exchange) {
        this.maxPathVarCount = maxPathVarCount;
        this.exchange = exchange;
    }

    public ScriptHandler getHandler() {
        return handler;
    }

    @Override
    public boolean matched(int nodeId, MethodSlotHandler handler) {
        return (this.handler = handler.getHandler(exchange.getRequestMethod())) != null;
    }

    public void invokeHandler() throws FiberException {
        ScriptContext context = new ScriptContext(exchange, handler.getVarConfigSource());
        context.fillPathVar(this, handler.getVarDefinitions());
        handler.invoke(exchange);
    }

    @Override
    public void addPathVar(String var, String value) {
        if (pathVars == null) {
            pathVars = new String[maxPathVarCount];
        }
        pathVars[pathVarCount++] = var;
    }

    @Override
    public void popPathVar() {
        pathVarCount--;
    }

}
