package io.fiber.net.proxy.lib;

import io.fiber.net.common.HttpExchange;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RespFunc {
    static class SetHeader implements SyncHttpFunc {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (context.getArgCnt() < 2) {
                throw new ScriptExecException("set header require key value");
            }

            String name = context.getArgVal(0).textValue();
            String value = context.getArgVal(1).asText();
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                throw new ScriptExecException("set header require string key value");
            }
            exchange.setResponseHeader(name, value);
            return NullNode.getInstance();
        }
    }

    static class AddHeader implements SyncHttpFunc {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (context.getArgCnt() < 2) {
                throw new ScriptExecException("add header require key value");
            }

            String name = context.getArgVal(0).textValue();
            String value = context.getArgVal(1).asText();
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                throw new ScriptExecException("add header require string key value");
            }
            exchange.addResponseHeader(name, value);
            return NullNode.getInstance();
        }
    }

    static class SendJson implements SyncHttpFunc {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (context.getArgCnt() < 2) {
                throw new ScriptExecException("send json require status and body");
            }
            try {
                exchange.writeJson(context.getArgVal(0).asInt(200), context.getArgVal(1));
            } catch (Exception e) {
                throw new ScriptExecException("error send json", e);
            }
            return NullNode.getInstance();
        }
    }

    static class Send implements SyncHttpFunc {
        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (context.getArgCnt() < 2) {
                throw new ScriptExecException("send require status and body");
            }

            JsonNode body = context.getArgVal(1);
            int status = context.getArgVal(0).asInt(200);
            if (body.isBinary()) {
                try {
                    exchange.writeRawBytes(status, Unpooled.wrappedBuffer(body.binaryValue()));
                } catch (Exception e) {
                    throw new ScriptExecException("error write binary response", e);
                }
            } else if (body.isTextual()) {
                exchange.setRequestHeader(Constant.CONTENT_TYPE_HEADER, "text/plain;charset=utf-8");
                try {
                    exchange.writeJson(status, Unpooled.wrappedBuffer(body.textValue().getBytes(StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    throw new ScriptExecException("error textual response", e);
                }
            } else {
                try {
                    exchange.writeJson(status, body);
                } catch (Exception e) {
                    throw new ScriptExecException("error send json", e);
                }
            }
            return NullNode.getInstance();
        }
    }

    static final Map<String, SyncHttpFunc> FC_MAP = new HashMap<>();

    static {
        FC_MAP.put("resp.setHeader", new SetHeader());
        FC_MAP.put("resp.addHeader", new AddHeader());
        FC_MAP.put("resp.sendJson", new SendJson());
        FC_MAP.put("resp.send", new Send());
    }

}
