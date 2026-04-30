package io.fiber.net.proxy.lib;

import io.fiber.net.common.json.*;
import io.fiber.net.common.utils.*;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;
import io.fiber.net.server.HttpExchange;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@ScriptLib(functionPrefix = "req")
public class ReqFunc {
    private static final ObjectNode EMPTY = JsonUtil.createObjectNode();

    public static class Ctx {
        private final HttpExchange exchange;
        private ObjectNode query;
        private ObjectNode headers;
        private ObjectNode cookies;

        private Ctx(HttpExchange exchange) {
            this.exchange = exchange;
        }

        public ObjectNode getQuery() {
            if (query == null) {
                String queryText = exchange.getQuery();
                if (StringUtils.isNotEmpty(queryText)) {
                    query = JsonUtil.createObjectNode();
                    UrlEncoded.decodeUtf8To(queryText, 0, queryText.length(), query::put);
                } else {
                    query = EMPTY;
                }
            }
            return query;
        }


        public ObjectNode getHeaders() {
            if (headers == null) {
                headers = JsonUtil.createObjectNode();
                Collection<String> requestNames = exchange.getRequestHeaderNames();
                for (String requestName : requestNames) {
                    headers.put(requestName, exchange.getRequestHeader(requestName));
                }
            }
            return headers;
        }

        public ObjectNode getCookies() {
            ObjectNode cookieNodes = this.cookies;
            if (cookieNodes == null) {
                cookieNodes = this.cookies = JsonUtil.createObjectNode();
                ObjectNode finalCookieNodes = cookieNodes;
                List<String> cookies = exchange.getRequestHeaderList("cookie");
                if (CollectionUtils.isNotEmpty(cookies)) {
                    for (String cookie : cookies) {
                        ServerCookieDecodeUtil.decode(cookie, true, (src, nameBegin, nameEnd, valueBegin, valueEnd, wrap) -> {
                            String name = src.substring(nameBegin, nameEnd);
                            String value = src.substring(valueBegin, valueEnd);
                            finalCookieNodes.put(name, value);
                            return false;
                        });
                    }
                }
            }
            return cookieNodes;
        }

    }

    private static final HttpExchange.Attr<Ctx> CTX_ATTR = HttpExchange.createAttr();

    public static Ctx getOrCreateCtx(ExecutionContext context) {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        Ctx ctx = CTX_ATTR.get(exchange);
        if (ctx != null) {
            return ctx;
        }
        CTX_ATTR.set(exchange, ctx = new Ctx(exchange));
        return ctx;
    }


    @ScriptFunction(name = "getHeader", constExpr = false)
    public static JsonNode getHeader(ExecutionContext context) {
        return getOrCreateCtx(context).getHeaders();
    }

    @ScriptFunction(name = "getHeader", constExpr = false)
    public static JsonNode getHeader(ExecutionContext context, @ScriptParam("name") JsonNode name) {
        String texted = name.textValue();
        if (StringUtils.isEmpty(texted)) {
            return NullNode.getInstance();
        }
        String header = HttpDynamicFunc.httpExchange(context).getRequestHeader(texted);
        if (header == null) {
            return MissingNode.getInstance();
        }
        return TextNode.valueOf(header);
    }

    @ScriptFunction(name = "getQuery", constExpr = false)
    public static JsonNode getQuery(ExecutionContext context) {
        return getOrCreateCtx(context).getQuery();
    }

    @ScriptFunction(name = "getQuery", constExpr = false)
    public static JsonNode getQuery(ExecutionContext context, @ScriptParam("name") JsonNode name) {
        String texted = name.textValue();
        if (StringUtils.isEmpty(texted)) {
            return null;
        }
        return getOrCreateCtx(context).getQuery().get(texted);
    }

    @ScriptFunction(name = "readJson", constExpr = false)
    public static void readJson(ExecutionContext context, Library.AsyncHandle handle) {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        exchange.readFullBody().subscribe((buf, throwable) -> {
            if (throwable != null) {
                handle.throwErr(new ScriptExecException(throwable.getMessage(), throwable, 400,
                        ScriptExecException.ERROR_NAME));
                return;
            }

            if (buf == null) {
                handle.throwErr(new ScriptExecException("client did not sent body", 400,
                        ScriptExecException.ERROR_NAME));
                return;
            }

            if (buf.readableBytes() == 0) {
                buf.release();
                handle.throwErr(new ScriptExecException("client did not sent body", 400,
                        ScriptExecException.ERROR_NAME));
                return;
            }

            JsonNode node;
            try {
                node = JsonUtil.readTree(new ByteBufInputStream(buf));
            } catch (IOException e) {
                handle.throwErr(new ScriptExecException(e.getMessage(), e, 400,
                        ScriptExecException.ERROR_NAME));
                return;
            } finally {
                buf.release();
            }
            handle.returnVal(node);
        });
    }

    @ScriptFunction(name = "readBinary", constExpr = false)
    public static void readBinary(ExecutionContext context, Library.AsyncHandle handle) {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        exchange.readFullBody().subscribe((buf, throwable) -> {
            if (throwable != null) {
                handle.throwErr(new ScriptExecException(throwable.getMessage(), throwable, 400,
                        ScriptExecException.ERROR_NAME));
                return;
            }
            if (buf == null) {
                handle.returnVal(BinaryNode.valueOf(Constant.EMPTY_BYTE_ARR));
                return;
            }

            if (buf.readableBytes() == 0) {
                buf.release();
                handle.returnVal(BinaryNode.valueOf(Constant.EMPTY_BYTE_ARR));
                return;
            }

            JsonNode node;
            try {
                node = BinaryNode.valueOf(ByteBufUtil.getBytes(buf));
            } finally {
                buf.release();
            }
            handle.returnVal(node);
        });
    }

    @ScriptFunction(name = "discardBody", constExpr = false)
    public static JsonNode discardBody(ExecutionContext context) {
        HttpDynamicFunc.httpExchange(context).discardReqBody();
        return NullNode.getInstance();
    }

    @ScriptFunction(name = "getUri", constExpr = false)
    public static JsonNode getUri(ExecutionContext context) {
        return TextNode.valueOf(HttpDynamicFunc.httpExchange(context).getUri());
    }

    @ScriptFunction(name = "getPath", constExpr = false)
    public static JsonNode getPath(ExecutionContext context) {
        return TextNode.valueOf(HttpDynamicFunc.httpExchange(context).getPath());
    }

    @ScriptFunction(name = "getQueryStr", constExpr = false)
    public static JsonNode getQueryStr(ExecutionContext context) {
        return TextNode.valueOf(HttpDynamicFunc.httpExchange(context).getQuery());
    }

    @ScriptFunction(name = "getMethod", constExpr = false)
    public static JsonNode getMethod(ExecutionContext context) {
        return Constant.METHOD_TEXTS[HttpDynamicFunc.httpExchange(context).getRequestMethod().ordinal()];
    }

    @ScriptFunction(name = "getCookie", constExpr = false)
    public static JsonNode getCookie(ExecutionContext context) {
        return getOrCreateCtx(context).getCookies();
    }

    @ScriptFunction(name = "getCookie", constExpr = false)
    public static JsonNode getCookie(ExecutionContext context, @ScriptParam("name") JsonNode name) {
        return getOrCreateCtx(context).getCookies().path(name.asText());
    }
}
