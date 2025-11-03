package io.fiber.net.proxy;

import io.fiber.net.common.ext.RouterHandler;
import io.fiber.net.common.json.ObjectNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.server.HttpExchange;

public class RouteContext implements RoutePathMatcher.MappingContext<MethodSlotHandler> {
    private static final HttpExchange.Attr<RouteContext> ATTR = HttpExchange.createAttr();

    public static RouteContext of(HttpExchange exchange) {
        return ATTR.get(exchange);
    }

    private final int maxPathVarCount;
    private final HttpExchange exchange;
    private String[] pathVars;
    private int pathVarCount;
    private RouterHandler<HttpExchange> handler;
    private ObjectNode node;

    public RouteContext(int maxPathVarCount, HttpExchange exchange) {
        this.maxPathVarCount = maxPathVarCount;
        this.exchange = exchange;
        ATTR.set(exchange, this);
    }

    public RouterHandler<HttpExchange> getHandler() {
        return handler;
    }

    public ObjectNode getNode() {
        if (node == null) {
            node = JsonUtil.createObjectNode();
            for (int i = 0, l = pathVarCount << 1; i < l; i += 2) {
                node.put(pathVars[i], pathVars[i + 1]);
            }
        }
        return node;
    }

    @Override
    public boolean matched(int nodeId, MethodSlotHandler handler) {
        this.handler = handler.getHandler(exchange.getRequestMethod());
        return true;
    }

    @Override
    public void addPathVar(String var, String value) {
        if (pathVars == null) {
            pathVars = new String[maxPathVarCount << 1];
        }
        pathVars[pathVarCount++] = var;
        pathVars[pathVarCount++] = value;
    }

    @Override
    public void popPathVar() {
        pathVarCount -= 2;
    }
}
