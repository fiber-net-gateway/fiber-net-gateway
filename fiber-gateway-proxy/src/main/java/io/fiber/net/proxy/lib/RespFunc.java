package io.fiber.net.proxy.lib;

import io.fiber.net.common.json.BooleanNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;
import io.fiber.net.server.HttpExchange;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.CookieHeaderNames;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.nio.charset.StandardCharsets;

@ScriptLib(functionPrefix = "resp")
public class RespFunc {
    @ScriptFunction(name = "setHeader", constExpr = false)
    public static JsonNode setHeader(ExecutionContext context,
                                     @ScriptParam("name") JsonNode name,
                                     @ScriptParam("value") JsonNode value)
            throws ScriptExecException {
        String nameText = name.textValue();
        String valueText = value.asText();
        if (StringUtils.isEmpty(nameText) || StringUtils.isEmpty(valueText)) {
            throw new ScriptExecException("set header require string key value");
        }
        HttpDynamicFunc.httpExchange(context).setResponseHeader(nameText, valueText);
        return NullNode.getInstance();
    }

    @ScriptFunction(name = "addHeader", constExpr = false)
    public static JsonNode addHeader(ExecutionContext context,
                                     @ScriptParam("name") JsonNode name,
                                     @ScriptParam("value") JsonNode value)
            throws ScriptExecException {
        String nameText = name.textValue();
        String valueText = value.asText();
        if (StringUtils.isEmpty(nameText) || StringUtils.isEmpty(valueText)) {
            throw new ScriptExecException("add header require string key value");
        }
        HttpDynamicFunc.httpExchange(context).addResponseHeader(nameText, valueText);
        return NullNode.getInstance();
    }

    @ScriptFunction(name = "sendJson", constExpr = false)
    public static JsonNode sendJson(ExecutionContext context,
                                    @ScriptParam("status") JsonNode status,
                                    @ScriptParam("body") JsonNode body)
            throws ScriptExecException {
        try {
            HttpDynamicFunc.httpExchange(context).writeJson(status.asInt(200), body);
        } catch (Exception e) {
            throw new ScriptExecException("error send json", e);
        }
        return NullNode.getInstance();
    }

    @ScriptFunction(name = "send", constExpr = false)
    public static JsonNode send(ExecutionContext context, @ScriptParam("status") JsonNode status)
            throws ScriptExecException {
        HttpDynamicFunc.httpExchange(context).writeRawBytes(status.asInt(200), Unpooled.EMPTY_BUFFER);
        return NullNode.getInstance();
    }

    @ScriptFunction(name = "send", constExpr = false)
    public static JsonNode send(ExecutionContext context,
                                @ScriptParam("status") JsonNode status,
                                @ScriptParam("body") JsonNode body)
            throws ScriptExecException {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        int statusCode = status.asInt(200);
        if (body.isBinary()) {
            try {
                exchange.writeRawBytes(statusCode, Unpooled.wrappedBuffer(body.binaryValue()));
            } catch (Exception e) {
                throw new ScriptExecException("error write binary response", e);
            }
        } else if (body.isTextual()) {
            exchange.setRequestHeader(Constant.CONTENT_TYPE_HEADER, "text/plain;charset=utf-8");
            try {
                String charSequence = body.textValue();
                ByteBuf buf = ByteBufAllocator.DEFAULT.buffer((int) (charSequence.length() * 1.5f) + 8);
                buf.writeCharSequence(charSequence, StandardCharsets.UTF_8);
                exchange.writeRawBytes(statusCode, buf);
            } catch (Exception e) {
                throw new ScriptExecException("error textual response", e);
            }
        } else {
            try {
                exchange.writeJson(statusCode, body);
            } catch (Exception e) {
                throw new ScriptExecException("error send json", e);
            }
        }
        return NullNode.getInstance();
    }

    @ScriptFunction(name = "addCookie", constExpr = false)
    public static JsonNode addCookie(ExecutionContext context, @ScriptParam("cookie") JsonNode node) {
        if (!node.isObject()) {
            return BooleanNode.FALSE;
        }

        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        String name = node.path("name").textValue();
        if (StringUtils.isEmpty(name)) {
            return BooleanNode.FALSE;
        }
        String value = node.path("value").asText();
        String domain = node.path("domain").textValue();
        String path = node.path("path").textValue();
        long maxAge = node.path("maxAge").asLong(Cookie.UNDEFINED_MAX_AGE);
        boolean secure = node.path("secure").asBoolean();
        boolean httpOnly = node.path("httpOnly").asBoolean();
        String sameSite = node.path("sameSite").textValue();

        String encode;
        try {
            DefaultCookie cookie = new DefaultCookie(name, value);
            cookie.setDomain(domain);
            cookie.setPath(path);
            cookie.setMaxAge(maxAge);
            cookie.setSecure(secure);
            cookie.setHttpOnly(httpOnly);
            cookie.setSameSite(ofSameSite(sameSite));
            encode = ServerCookieEncoder.STRICT.encode(cookie);
        } catch (RuntimeException ignore) {
            return BooleanNode.FALSE;
        }
        exchange.addResponseHeader("Set-Cookie", encode);
        return BooleanNode.TRUE;
    }

    private static CookieHeaderNames.SameSite ofSameSite(String sameSite) {
        if (StringUtils.isEmpty(sameSite)) {
            return null;
        }
        switch (sameSite) {
            case "Lax":
                return CookieHeaderNames.SameSite.Lax;
            case "Strict":
                return CookieHeaderNames.SameSite.Strict;
            case "None":
                return CookieHeaderNames.SameSite.None;
            default:
                return null;
        }
    }
}
