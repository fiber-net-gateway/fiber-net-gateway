package io.fiber.net.proxy.lib;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.FunctionParam;
import io.fiber.net.script.FunctionSignature;
import io.fiber.net.script.Library;
import io.fiber.net.server.HttpExchange;

public class TunnelProxyAuth implements SyncHttpFunc {
    private static final FunctionSignature SIGNATURE = FunctionSignature.fixed("req.tunnelProxyAuth", false,
            FunctionParam.optional("auth", TextNode.valueOf("Basic xxxx")));

    @Override
    public FunctionSignature signature() {
        return SIGNATURE;
    }

    @Override
    public JsonNode call(ExecutionContext context, Library.Arguments args) {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        if (args.noArgs()) {
            exchange.setResponseHeaderUnsafe("Proxy-Authenticate", "Basic xxxx");
        } else {
            exchange.setResponseHeaderUnsafe("Proxy-Authenticate", args.getArgVal(0).asText("Basic xxxx"));
        }
        exchange.writeJson(407, "auth required");
        return NullNode.getInstance();
    }
}
