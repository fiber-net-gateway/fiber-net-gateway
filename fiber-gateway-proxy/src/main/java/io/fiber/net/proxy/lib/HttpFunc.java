package io.fiber.net.proxy.lib;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.fiber.net.common.FiberException;
import io.fiber.net.common.HttpExchange;
import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.internal.SerializeJsonObservable;
import io.fiber.net.common.utils.ArrayUtils;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.http.util.UrlEncoded;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HttpFunc implements Library.DirectiveDef {

    private final HttpHost httpHost;
    private final HttpClient httpClient;
    private final Map<String, HttpDynamicFunc> fc = new HashMap<>();

    public HttpFunc(HttpHost httpHost, HttpClient httpClient) {
        this.httpHost = httpHost;
        this.httpClient = httpClient;
        fc.put("send", new SendFunc());
        fc.put("proxyPass", new ProxyFunc());
    }

    @Override
    public Library.Function findFunc(String directive, String function) {
        return fc.get(function);
    }

    private class SendFunc implements HttpDynamicFunc {
        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            JsonNode param = ArrayUtils.isNotEmpty(args) ? args[0] : NullNode.getInstance();
            ClientExchange exchange = httpClient.refer(httpHost);
            setMethod(param, HttpMethod.GET, exchange);
            setUri(param, "/", null, exchange);
            addHeader(exchange, param);

            JsonNode node = param.get("body");
            if (node != null) {
                if (node.isBinary()) {
                    exchange.setReqBufFullFunc(ec -> Unpooled.wrappedBuffer(node.binaryValue()));
                } else {
                    exchange.setHeader(Constant.CONTENT_TYPE_HEADER, Constant.CONTENT_TYPE_JSON_UTF8);
                    exchange.setReqBodyFunc(ec -> new SerializeJsonObservable(node, ByteBufAllocator.DEFAULT), false);
                }
            }
            exchange.sendForResp().subscribe((response, e) -> {
                if (e != null) {
                    context.throwErr(this, new ScriptExecException("error send http", e));
                } else {
                    ObjectNode nodes = JsonUtil.MAPPER.createObjectNode();
                    nodes.put("status", response.status());
                    response.readFullRespBody().subscribe((buf, e2) -> {
                        byte[] bytes = ByteBufUtil.getBytes(buf);
                        nodes.put("body", bytes);
                        buf.release();
                        context.returnVal(this, nodes);
                    });
                }
            });
        }
    }

    private static void setMethod(JsonNode param, HttpMethod mtd, ClientExchange exchange) {
        JsonNode node = param.get("method");
        if (node != null && node.isTextual()) {
            try {
                mtd = HttpMethod.valueOf(node.textValue().toUpperCase());
            } catch (RuntimeException ignore) {
            }
        }
        exchange.setMethod(mtd);
    }

    private static void addHeader(ClientExchange exchange, JsonNode param) {
        JsonNode node = param.get("headers");
        if (JsonUtil.isNull(node) || !node.isObject() || node.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        do {
            Map.Entry<String, JsonNode> next = fields.next();
            String key = next.getKey();
            JsonNode jsonNode = next.getValue();
            if (JsonUtil.isNull(jsonNode)) {
                exchange.removeHeader(key);
            } else if (jsonNode.isTextual() && StringUtils.isNotEmpty(jsonNode.textValue())) {
                exchange.setHeader(key, jsonNode.textValue());
            }
        } while (fields.hasNext());
    }

    private static void setUri(JsonNode param, String path, String query, ClientExchange exchange) {
        JsonNode node;
        node = param.get("path");
        if (node != null && node.isTextual() && StringUtils.isNotEmpty(node.textValue())) {
            path = node.textValue();
        }
        String uri = path;
        node = param.get("query");
        if (node != null) {
            if (node.isTextual() && StringUtils.isNotEmpty(node.textValue())) {
                uri = uri + '?' + node.textValue();
            } else if (node.isObject() && !node.isEmpty()) {
                StringBuilder builder = new StringBuilder(uri.length() + 64);
                builder.append(uri).append('?');
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                do {
                    Map.Entry<String, JsonNode> next = fields.next();
                    JsonNode jsonNode = next.getValue();
                    String key = next.getKey();
                    if (jsonNode.isArray()) {
                        for (JsonNode n : jsonNode) {
                            UrlEncoded.encodeInto(key, StandardCharsets.UTF_8, builder);
                            builder.append('=');
                            UrlEncoded.encodeInto(JsonUtil.toString(n), StandardCharsets.UTF_8, builder);
                        }
                    } else {
                        UrlEncoded.encodeInto(key, StandardCharsets.UTF_8, builder);
                        builder.append('=');
                        UrlEncoded.encodeInto(JsonUtil.toString(jsonNode), StandardCharsets.UTF_8, builder);
                    }
                    builder.append('&');
                } while (fields.hasNext());
                builder.setLength(builder.length() - 1);
                uri = builder.toString();
            }
        } else if (StringUtils.isNotEmpty(query)) {
            uri = uri + '?' + query;
        }
        exchange.setUri(uri);
    }


    private class ProxyFunc implements HttpDynamicFunc {

        @Override
        public void call(ExecutionContext context, JsonNode... args) {
            HttpExchange httpExchange = HttpDynamicFunc.httpExchange(context);
            JsonNode param = ArrayUtils.isNotEmpty(args) ? args[0] : NullNode.getInstance();
            ClientExchange exchange = httpClient.refer(httpHost);
            setMethod(param, httpExchange.getRequestMethod(), exchange);
            setUri(param, httpExchange.getPath(), httpExchange.getQuery(), exchange);
            for (String headerName : httpExchange.getRequestHeaderNames()) {
                exchange.setHeader(headerName, httpExchange.getRequestHeader(headerName));
            }
            addHeader(exchange, param);
            exchange.setReqBodyFunc(ec -> httpExchange.readReqBody(), false);

            JsonNode node = param.get("responseHeaders");
            exchange.sendForResp().subscribe((response, e) -> {
                if (e != null) {
                    context.throwErr(this, new ScriptExecException(e.getMessage(), e));
                } else {
                    int status = response.status();
                    for (String name : response.getHeaderNames()) {
                        httpExchange.setResponseHeader(name, response.getHeader(name));
                    }
                    addResponseHeaders(node, httpExchange);
                    try {
                        httpExchange.writeRawBytes(status, response.readRespBody());
                    } catch (FiberException ex) {
                        context.throwErr(this, new ScriptExecException(ex.getMessage(), ex));
                        return;
                    }
                    context.returnVal(this, IntNode.valueOf(status));
                }
            });
        }
    }

    private static void addResponseHeaders(JsonNode node, HttpExchange exchange) {
        if (!JsonUtil.isNull(node) && node.isObject() && !node.isEmpty()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            do {
                Map.Entry<String, JsonNode> next = fields.next();
                String key = next.getKey();
                JsonNode jsonNode = next.getValue();
                if (JsonUtil.isNull(jsonNode) || jsonNode.isTextual() && StringUtils.isEmpty(jsonNode.isEmpty())) {
                    exchange.removeResponseHeader(key);
                    continue;
                }
                if (jsonNode.isTextual() && StringUtils.isNotEmpty(jsonNode.textValue())) {
                    exchange.setResponseHeader(key, jsonNode.textValue());
                }
            } while (fields.hasNext());
        }
    }

}
