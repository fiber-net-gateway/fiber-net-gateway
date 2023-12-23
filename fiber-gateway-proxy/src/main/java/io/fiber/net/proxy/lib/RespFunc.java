package io.fiber.net.proxy.lib;

import com.fasterxml.jackson.databind.JsonNode;
import io.fiber.net.common.HttpExchange;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RespFunc {
    static class SetHeader implements HttpDynamicFunc {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (args.length < 2) {
                context.throwErr(this, new ScriptExecException("set header require key value"));
                return;
            }

            String name = args[0].textValue();
            String value = args[1].asText();
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                context.throwErr(this, new ScriptExecException("set header require string key value"));
                return;
            }
            exchange.setResponseHeader(name, value);
            context.returnVal(this, null);
        }
    }

    static class AddHeader implements HttpDynamicFunc {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (args.length < 2) {
                context.throwErr(this, new ScriptExecException("add header require key value"));
                return;
            }

            String name = args[0].textValue();
            String value = args[1].asText();
            if (StringUtils.isEmpty(name) || StringUtils.isEmpty(value)) {
                context.throwErr(this, new ScriptExecException("add header require string key value"));
                return;
            }
            exchange.addResponseHeader(name, value);
        }
    }

    static class SendJson implements HttpDynamicFunc {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (args.length < 2) {
                context.throwErr(this, new ScriptExecException("send json require status and body"));
                return;
            }
            try {
                exchange.writeJson(args[0].asInt(200), args[1]);
            } catch (Exception e) {
                context.throwErr(this, new ScriptExecException("error send json", e));
            }
        }
    }

    static class Send implements HttpDynamicFunc {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            if (args.length < 2) {
                context.throwErr(this, new ScriptExecException("send require status and body"));
                return;
            }

            JsonNode body = args[1];
            int status = args[0].asInt(200);
            if (body.isBinary()) {
                try {
                    exchange.writeRawBytes(status, Unpooled.wrappedBuffer(body.binaryValue()));
                } catch (Exception e) {
                    context.throwErr(this, new ScriptExecException("error write binary response", e));
                }
            } else if (body.isTextual()) {
                exchange.setRequestHeader(Constant.CONTENT_TYPE_HEADER, "text/plain;charset=utf-8");
                try {
                    exchange.writeJson(status, Unpooled.wrappedBuffer(body.textValue().getBytes(StandardCharsets.UTF_8)));
                } catch (Exception e) {
                    context.throwErr(this, new ScriptExecException("error textual response", e));
                }
            } else {
                try {
                    exchange.writeJson(status, body);
                } catch (Exception e) {
                    context.throwErr(this, new ScriptExecException("error send json", e));
                }
            }
        }
    }

    static final Map<String, Library.Function> FC_MAP = new HashMap<>();

    static {
        FC_MAP.put("resp.setHeader", new SetHeader());
        FC_MAP.put("resp.addHeader", new AddHeader());
        FC_MAP.put("resp.sendJson", new SendJson());
        FC_MAP.put("resp.send", new Send());
    }

}
