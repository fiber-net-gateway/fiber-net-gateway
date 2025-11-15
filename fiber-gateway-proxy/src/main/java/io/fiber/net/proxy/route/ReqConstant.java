package io.fiber.net.proxy.route;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.proxy.lib.HttpDynamicFunc;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.server.HttpExchange;

public class ReqConstant {
    public static final SyncHttpConst URI = new Uri();
    public static final SyncHttpConst METHOD = new Method();
    public static final SyncHttpConst PATH = new Path();
    public static final SyncHttpConst QUERY = new Query();

    public static class Uri implements SyncHttpConst {
        @Override
        public JsonNode get(ExecutionContext executionContext) throws ScriptExecException {
            HttpExchange httpExchange = HttpDynamicFunc.httpExchange(executionContext);
            return TextNode.valueOf(httpExchange.getUri());
        }
    }

    public static class Path implements SyncHttpConst {
        @Override
        public JsonNode get(ExecutionContext executionContext) throws ScriptExecException {
            HttpExchange httpExchange = HttpDynamicFunc.httpExchange(executionContext);
            return TextNode.valueOf(httpExchange.getPath());
        }
    }

    public static class Method implements SyncHttpConst {
        private static final TextNode[] MTD;

        static {
            MTD = Constant.METHOD_TEXTS;
        }

        @Override
        public JsonNode get(ExecutionContext executionContext) throws ScriptExecException {
            HttpExchange httpExchange = HttpDynamicFunc.httpExchange(executionContext);
            return MTD[httpExchange.getRequestMethod().ordinal()];
        }
    }

    public static class Query implements SyncHttpConst {

        @Override
        public JsonNode get(ExecutionContext executionContext) throws ScriptExecException {
            HttpExchange httpExchange = HttpDynamicFunc.httpExchange(executionContext);
            return TextNode.valueOf(httpExchange.getQuery());
        }
    }

}
