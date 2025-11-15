package io.fiber.net.proxy.route;

import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.NullNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.CollectionUtils;
import io.fiber.net.common.utils.ServerCookieDecodeUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.common.utils.UrlEncoded;
import io.fiber.net.server.HttpExchange;

import java.util.Collection;
import java.util.List;

public abstract class AbstractRouteContext {
    private static final JsonNode[] EMPTY_VAR = new JsonNode[0];
    protected static final HttpExchange.Attr<AbstractRouteContext> ATTR = HttpExchange.createAttr();

    protected final ReusedTmpCs tmpCs = new ReusedTmpCs();
    protected final HttpExchange exchange;
    protected final VarConfigSource varConfigSource;
    protected JsonNode[] vars;
    private long varInitMask;

    protected AbstractRouteContext(HttpExchange exchange, VarConfigSource varConfigSource) {
        this.exchange = exchange;
        this.varConfigSource = varConfigSource;
        ATTR.set(exchange, this);
    }

    protected final JsonNode[] allocVars() {
        JsonNode[] nodes;
        if ((nodes = vars) == null) {
            int maxVarLength;
            nodes = vars = (maxVarLength = varConfigSource.getVarLength()) == 0 ? EMPTY_VAR : new JsonNode[maxVarLength];
        }
        return nodes;
    }

    public static JsonNode getVar(HttpExchange exchange, VarType varType, int idx) {
        return ATTR.get(exchange).getVar(varType, idx);
    }


    public JsonNode getVar(VarType varType, int idx) {
        int o = varType.ordinal();
        long mask = varInitMask;
        long bit;
        JsonNode[] jsonNodes = allocVars();
        if ((mask & (bit = 1L << o)) == 0) {
            varInitMask = mask | bit;
            switch (varType) {
                case QUERY:
                    parseQueryVar(jsonNodes);
                    break;
                case REQ_HEADER:
                    parseReqHeaderVar(jsonNodes);
                    break;
                case COOKIE:
                    parseReqCookieVar(jsonNodes);
                    break;
                case RESP_HEADER:
                    fillRespHeadersVar(jsonNodes);
                    break;
                case CONTEXT:
                    fillContextVar(jsonNodes);
                    break;
            }
        }

        JsonNode node = jsonNodes[idx];
        if (node == null) {
            node = jsonNodes[idx] = NullNode.getInstance();
        }
        return node;
    }

    protected abstract void fillContextVar(JsonNode[] jsonNodes);

    protected abstract void fillRespHeadersVar(JsonNode[] jsonNodes);

    protected void parseReqCookieVar(JsonNode[] jsonNodes) {
        List<String> cookie = exchange.getRequestHeaderList("Cookie");
        if (CollectionUtils.isNotEmpty(cookie)) {
            for (String c : cookie) {
                ServerCookieDecodeUtil.decode(c, true, (src, nameBegin, nameEnd, valueBegin, valueEnd, wrap) -> {
                    int varIdx = varConfigSource.getVarIdx(VarType.COOKIE, tmpCs.allocTmpCs(src, nameBegin, nameEnd));
                    if (varIdx >= 0) {
                        jsonNodes[varIdx] = valueEnd > valueBegin ? TextNode.valueOf(src.substring(valueBegin, valueEnd)) : TextNode.EMPTY_STRING_NODE;
                    }
                    return false;
                });
            }
        }
    }

    protected void parseReqHeaderVar(JsonNode[] jsonNodes) {
        HttpExchange exchange = this.exchange;
        VarConfigSource varConfigSource = this.varConfigSource;
        Collection<String> headerNames = exchange.getRequestHeaderNames();
        for (String name : headerNames) {
            int idx = varConfigSource.getVarIdx(VarType.REQ_HEADER, tmpCs.allocTmpCs(name, 0, name.length()));
            if (idx >= 0) {
                jsonNodes[idx] = TextNode.valueOf(exchange.getRequestHeader(name));
            }
        }
    }

    protected void parseQueryVar(JsonNode[] jsonNodes) {
        String query = exchange.getQuery();
        if (StringUtils.isNotEmpty(query)) {
            UrlEncoded.decodeUtf8To(query, 0, query.length(), (k, v) -> {
                int idx = varConfigSource.getVarIdx(VarType.QUERY, k);
                if (idx >= 0) {
                    jsonNodes[idx] = TextNode.valueOf(v);
                }
            });
        }
    }

}
