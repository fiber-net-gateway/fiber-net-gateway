package io.fiber.net.proxy.lib;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.server.HttpExchange;

public class TunnelProxyAuth implements SyncHttpFunc {
    @Override
    public JsonNode call(ExecutionContext context) {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        if (context.noArgs()) {
            exchange.setResponseHeaderUnsafe("Proxy-Authenticate", "Basic xxxx");
        } else {
            exchange.setResponseHeaderUnsafe("Proxy-Authenticate", context.getArgVal(0).asText("Basic xxxx"));
        }
        exchange.writeJson(407, "auth required");
        return NullNode.getInstance();
    }
}
