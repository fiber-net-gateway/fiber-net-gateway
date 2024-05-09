package io.fiber.net.proxy.lib;

import io.fiber.net.server.HttpExchange;
import io.fiber.net.common.json.*;
import io.fiber.net.common.utils.Constant;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.http.util.MultiMap;
import io.fiber.net.http.util.UrlEncoded;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufUtil;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReqFunc {
    private static final ObjectNode EMPTY = JsonUtil.createObjectNode();

    private static class Ctx {
        private final HttpExchange exchange;
        private ObjectNode query;
        private ObjectNode headers;

        private Ctx(HttpExchange exchange) {
            this.exchange = exchange;
        }

        public ObjectNode getQuery() {
            if (query == null) {
                String queryText = exchange.getQuery();
                if (StringUtils.isNotEmpty(queryText)) {
                    MultiMap<String> map = new MultiMap<>();
                    UrlEncoded.decodeUtf8To(queryText, map);
                    query = JsonUtil.createObjectNode();
                    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                        query.put(entry.getKey(), entry.getValue().get(0));
                    }
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

    }

    private static final HttpExchange.Attr<Ctx> CTX_ATTR = HttpExchange.createAttr();

    private static Ctx getOrCreateCtx(ExecutionContext context) {
        HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
        Ctx ctx = CTX_ATTR.get(exchange);
        if (ctx != null) {
            return ctx;
        }
        CTX_ATTR.set(exchange, ctx = new Ctx(exchange));
        return ctx;
    }


    private static class GetHeader implements SyncHttpFunc {

        @Override
        public JsonNode call(ExecutionContext context) {
            Ctx ctx = getOrCreateCtx(context);
            if (context.noArgs()) {
                return ctx.getHeaders();
            } else {
                String texted = context.getArgVal(0).textValue();
                if (StringUtils.isEmpty(texted)) {
                    return NullNode.getInstance();
                }
                return ctx.getHeaders().get(texted);
            }
        }
    }

    private static class GetQuery implements SyncHttpFunc {

        @Override
        public JsonNode call(ExecutionContext context) {
            Ctx ctx = getOrCreateCtx(context);
            if (context.noArgs()) {
                return ctx.getQuery();
            } else {
                String texted = context.getArgVal(0).textValue();
                if (StringUtils.isEmpty(texted)) {
                    return null;
                }
                return ctx.getQuery().get(texted);
            }
        }
    }

    private static class ReadJsonBody implements HttpDynamicFunc {
        @Override
        public void call(ExecutionContext context) {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            exchange.readFullReqBody().subscribe((buf, throwable) -> {
                if (throwable != null) {
                    context.throwErr(new ScriptExecException(throwable.getMessage(), throwable, 400,
                            ScriptExecException.ERROR_NAME));
                    return;
                }

                if (buf == null) {
                    context.throwErr(new ScriptExecException("client did not sent body", 400,
                            ScriptExecException.ERROR_NAME));
                    return;
                }

                if (buf.readableBytes() == 0) {
                    buf.release();
                    context.throwErr(new ScriptExecException("client did not sent body", 400,
                            ScriptExecException.ERROR_NAME));
                    return;
                }

                JsonNode node;
                try {
                    node = JsonUtil.readTree(new ByteBufInputStream(buf));
                } catch (IOException e) {
                    context.throwErr(new ScriptExecException(e.getMessage(), e, 400,
                            ScriptExecException.ERROR_NAME));
                    return;
                } finally {
                    buf.release();
                }
                context.returnVal(node);
            });
        }
    }

    private static class ReadBinaryBody implements HttpDynamicFunc {
        @Override
        public void call(ExecutionContext context) {
            HttpExchange exchange = HttpDynamicFunc.httpExchange(context);
            exchange.readFullReqBody().subscribe((buf, throwable) -> {
                if (throwable != null) {
                    context.throwErr(new ScriptExecException(throwable.getMessage(), throwable, 400,
                            ScriptExecException.ERROR_NAME));
                }
                if (buf == null) {
                    context.returnVal(BinaryNode.valueOf(Constant.EMPTY_BYTE_ARR));
                    return;
                }

                if (buf.readableBytes() == 0) {
                    buf.release();
                    context.returnVal(BinaryNode.valueOf(Constant.EMPTY_BYTE_ARR));
                    return;
                }

                JsonNode node;
                try {
                    node = BinaryNode.valueOf(ByteBufUtil.getBytes(buf));
                } finally {
                    buf.release();
                }
                context.returnVal(node);
            });
        }
    }

    private static class DiscardBody implements SyncHttpFunc {
        @Override
        public JsonNode call(ExecutionContext context) {
            HttpDynamicFunc.httpExchange(context).discardReqBody();
            return NullNode.getInstance();
        }
    }

    private static class GetPath implements SyncHttpFunc {
        @Override
        public JsonNode call(ExecutionContext context) {
            return TextNode.valueOf(HttpDynamicFunc.httpExchange(context).getPath());
        }
    }

    private static class GetQueryText implements SyncHttpFunc {
        @Override
        public JsonNode call(ExecutionContext context) {
            return TextNode.valueOf(HttpDynamicFunc.httpExchange(context).getPath());
        }
    }

    private static class GetMethodText implements SyncHttpFunc {
        private static final TextNode[] MTD = Constant.METHOD_TEXTS;

        @Override
        public JsonNode call(ExecutionContext context) {
            return MTD[HttpDynamicFunc.httpExchange(context).getRequestMethod().ordinal()];
        }
    }

    static final Map<String, Library.AsyncFunction> ASYNC_FC_MAP = new HashMap<>();
    static final Map<String, Library.Function> FC_MAP = new HashMap<>();

    static {
        FC_MAP.put("req.getPath", new GetPath());
        FC_MAP.put("req.getQueryStr", new GetQueryText());
        FC_MAP.put("req.getMethod", new GetMethodText());
        FC_MAP.put("req.getHeader", new GetHeader());
        FC_MAP.put("req.getQuery", new GetQuery());
        FC_MAP.put("req.discardBody", new DiscardBody());
        ASYNC_FC_MAP.put("req.readJson", new ReadJsonBody());
        ASYNC_FC_MAP.put("req.readBinary", new ReadBinaryBody());
    }

}
