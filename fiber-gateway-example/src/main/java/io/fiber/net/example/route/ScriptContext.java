package io.fiber.net.example.route;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.proxy.route.AbstractRouteContext;
import io.fiber.net.proxy.route.VarConfigSource;
import io.fiber.net.server.HttpExchange;

public class ScriptContext extends AbstractRouteContext {
    public static ScriptContext of(HttpExchange exchange) {
        return (ScriptContext) ATTR.get(exchange);
    }

    protected ScriptContext(HttpExchange exchange, VarConfigSource varConfigSource) {
        super(exchange, varConfigSource);
    }

    @Override
    protected void fillContextVar(JsonNode[] jsonNodes) {

    }

    public void fillPathVar(RouteContext context, int[] varDefinitions) {
        if (varDefinitions == null) {
            // not need path var in script
            return;
        }
        String[] pathVars = context.pathVars;
        int len = context.pathVarCount;
        int varOffset = context.varOffset;
        JsonNode[] jsonNodes = allocVars();
        for (int i = 0; i < len; i++) {
            int idx = varDefinitions[varOffset + i];
            if (idx != -1) {
                jsonNodes[idx] = TextNode.valueOf(pathVars[i]);
            }
        }
    }

    @Override
    protected void fillRespHeadersVar(JsonNode[] jsonNodes) {

    }
}
