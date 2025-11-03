package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ObjectNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.common.utils.UrlEncoded;
import io.fiber.net.script.ExecutionContext;
import io.fiber.net.script.Library;
import io.fiber.net.script.ScriptExecException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class UrlFunc {
    public static final Map<String, Library.Function> FUNC = new HashMap<>();

    static {
        FUNC.put("URL.encodeComponent", new EncodeComponentFunc());
        FUNC.put("URL.decodeComponent", new DecodeComponentFunc());
        FUNC.put("URL.parseQuery", new ParseQueryFunc());
        FUNC.put("URL.buildQuery", new BuildQueryFunc());
    }

    private static class EncodeComponentFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.getArgCnt() < 1) {
                throw new ScriptExecException("encode component require at least one argument");
            }
            JsonNode val = context.getArgVal(0);
            if (!val.isTextual()) {
                throw new ScriptExecException("encode component require text value");
            }
            String value = val.textValue();
            if (StringUtils.isEmpty(value)) {
                return TextNode.EMPTY_STRING_NODE;
            }
            return TextNode.valueOf(UrlEncoded.encode(value));
        }
    }

    private static class DecodeComponentFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.getArgCnt() < 1) {
                throw new ScriptExecException("decode component require at least one argument");
            }
            JsonNode val = context.getArgVal(0);
            if (!val.isTextual()) {
                throw new ScriptExecException("decode component require text value");
            }
            String value = val.textValue();
            if (StringUtils.isEmpty(value)) {
                return TextNode.EMPTY_STRING_NODE;
            }
            return TextNode.valueOf(UrlEncoded.decode(value));
        }
    }

    private static class ParseQueryFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.getArgCnt() < 1) {
                throw new ScriptExecException("parse query require at least one argument");
            }
            JsonNode val = context.getArgVal(0);
            if (!val.isTextual()) {
                throw new ScriptExecException("parse query require text value");
            }
            String value = val.textValue();
            ObjectNode node = JsonUtil.createObjectNode();
            if (StringUtils.isEmpty(value)) {
                return node;
            }

            try {
                UrlEncoded.decodeUtf8To(value, 0, value.length(), (k, v) -> {
                    if (node.has(k)) {
                        JsonNode old = node.get(k);
                        if (old.isArray()) {
                            ((ArrayNode) old).add(v);
                        } else {
                            ArrayNode array = JsonUtil.createArrayNode();
                            array.add(old);
                            array.add(v);
                            node.set(k, array);
                        }
                    } else {
                        node.put(k, v);
                    }
                });
            } catch (Exception e) {
                throw ScriptExecException.fromThrowable(e);
            }
            return node;
        }
    }

    private static class BuildQueryFunc implements Library.Function {

        @Override
        public JsonNode call(ExecutionContext context) throws ScriptExecException {
            if (context.getArgCnt() < 1) {
                throw new ScriptExecException("build query require at least one argument");
            }
            JsonNode val = context.getArgVal(0);

            if (val.isNull() || val.isMissingNode()) {
                return val;
            }

            if (!val.isObject()) {
                throw new ScriptExecException("build query require object value");
            }
            if (val.isEmpty()) {
                return TextNode.EMPTY_STRING_NODE;
            }

            StringBuilder sb = new StringBuilder(128);

            Iterator<Map.Entry<String, JsonNode>> fields = val.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> next = fields.next();
                String key = next.getKey();
                JsonNode jsonNode = next.getValue();
                if (jsonNode.isArray()) {
                    for (JsonNode node : jsonNode) {
                        UrlEncoded.encodeInto(key, StandardCharsets.UTF_8, sb);
                        sb.append('=');
                        UrlEncoded.encodeInto(JsonUtil.toString(node), StandardCharsets.UTF_8, sb);
                        sb.append('&');
                    }
                } else {
                    UrlEncoded.encodeInto(key, StandardCharsets.UTF_8, sb);
                    sb.append('=');
                    UrlEncoded.encodeInto(JsonUtil.toString(jsonNode), StandardCharsets.UTF_8, sb);
                    sb.append('&');
                }
            }

            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '&') {
                sb.setLength(sb.length() - 1);
            }
            return TextNode.valueOf(sb.toString());
        }
    }
}
