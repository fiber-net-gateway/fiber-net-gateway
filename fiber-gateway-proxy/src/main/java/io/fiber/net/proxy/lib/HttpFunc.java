package io.fiber.net.proxy.lib;

import io.fiber.net.common.HttpMethod;
import io.fiber.net.common.async.internal.SerializeJsonObservable;
import io.fiber.net.common.json.*;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.ClientExchange;
import io.fiber.net.http.ClientResponse;
import io.fiber.net.http.HttpClient;
import io.fiber.net.http.HttpHost;
import io.fiber.net.http.util.UrlEncoded;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.server.HttpExchange;
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
        fc.put("request", new SendFunc());
        fc.put("proxyPass", new ProxyFunc());
    }

    @Override
    public Library.Function findFunc(String directive, String function) {
        return null;
    }

    @Override
    public Library.AsyncFunction findAsyncFunc(String directive, String function) {
        return fc.get(function);
    }

    private class SendFunc implements HttpDynamicFunc {
        @Override
        public void call(ExecutionContext context) {
            JsonNode param = context.getArgCnt() > 0 ? context.getArgVal(0) : NullNode.getInstance();
            ClientExchange exchange = httpClient.refer(httpHost);
            setMethod(param, HttpMethod.GET, exchange);
            setUri(param, "/", null, exchange);
            addHeader(exchange, param);
            setTimeout(param, exchange);
            JsonNode node = param.get("body");
            if (node != null) {
                if (node.isBinary()) {
                    exchange.setReqBufFullFunc(ec -> Unpooled.wrappedBuffer(node.binaryValue()));
                } else {
                    exchange.setHeader(Constant.CONTENT_TYPE_HEADER, Constant.CONTENT_TYPE_JSON_UTF8);
                    exchange.setReqBodyFunc(ec -> new SerializeJsonObservable(node, ByteBufAllocator.DEFAULT), false);
                }
            }
            boolean includeHeaders = param.path("includeHeaders").asBoolean();
            exchange.sendForResp().subscribe((response, e) -> {
                if (e != null) {
                    context.throwErr(new ScriptExecException("error send http", e));
                } else {
                    ObjectNode nodes = JsonUtil.createObjectNode();
                    nodes.put("status", response.status());
                    if (includeHeaders) {
                        nodes.set("headers", readHeaders(response));
                    }

                    response.readFullRespBody().subscribe((buf, e2) -> {
                        if (e2 != null) {
                            context.throwErr(ScriptExecException.fromThrowable(e2));
                            return;
                        }
                        if (buf != null) {
                            byte[] bytes = ByteBufUtil.getBytes(buf);
                            buf.release();
                            nodes.put("body", bytes);
                        } else {
                            nodes.set("body", BinaryNode.getEmpty());
                        }
                        context.returnVal(nodes);
                    });
                }
            });
        }
    }

    private static ObjectNode readHeaders(ClientResponse ce) {
        ObjectNode hs = JsonUtil.createObjectNode();
        for (String headerName : ce.getHeaderNames()) {
            hs.put(headerName, ce.getHeader(headerName));
        }
        return hs;
    }

    private static void setMethod(JsonNode param, HttpMethod mtd, ClientExchange exchange) {
        JsonNode node = param.get("method");
        if (node != null && node.isTextual()) {
            HttpMethod resolved = HttpMethod.resolve(node.textValue().toUpperCase());
            if (resolved != null) {
                mtd = resolved;
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
        public void call(ExecutionContext context) {
            HttpExchange httpExchange = HttpDynamicFunc.httpExchange(context);
            JsonNode param = context.getArgCnt() > 0 ? context.getArgVal(0) : NullNode.getInstance();
            ClientExchange exchange = httpClient.refer(httpHost);
            setMethod(param, httpExchange.getRequestMethod(), exchange);
            setUri(param, httpExchange.getPath(), httpExchange.getQuery(), exchange);
            setTimeout(param, exchange);
            for (String headerName : httpExchange.getRequestHeaderNames()) {
                exchange.addHeader(headerName, httpExchange.getRequestHeaderList(headerName));
            }
            addHeader(exchange, param);
            exchange.setReqBodyFunc(ec -> httpExchange.readBodyUnsafe(), false);

            JsonNode node = param.get("responseHeaders");
            exchange.sendForResp().subscribe((response, e) -> {
                if (e != null) {
                    context.throwErr(new ScriptExecException(e.getMessage(), e));
                } else {
                    int status = response.status();
                    for (String name : response.getHeaderNames()) {
                        httpExchange.addResponseHeader(name, response.getHeaderList(name));
                    }
                    addResponseHeaders(node, httpExchange);
                    httpExchange.writeRawBytes(status, response.readRespBodyUnsafe());
                    context.returnVal(IntNode.valueOf(status));
                }
            });
        }
    }

    private static void setTimeout(JsonNode param, ClientExchange exchange) {
        JsonNode node = param.get("timeout");
        if (node == null || !node.isIntegralNumber()) {
            return;
        }
        int timeout = node.intValue();
        exchange.setRequestTimeout(timeout);
        exchange.setConnectTimeout(timeout);
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
