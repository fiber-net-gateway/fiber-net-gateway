package io.fiber.net.script.std;

import io.fiber.net.common.json.ArrayNode;
import io.fiber.net.common.json.JsonNode;
import io.fiber.net.common.json.ObjectNode;
import io.fiber.net.common.json.TextNode;
import io.fiber.net.common.utils.JsonUtil;
import io.fiber.net.common.utils.StringUtils;
import io.fiber.net.common.utils.UrlEncoded;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.lib.ScriptFunction;
import io.fiber.net.script.lib.ScriptLib;
import io.fiber.net.script.lib.ScriptParam;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

@ScriptLib(functionPrefix = "URL")
public final class UrlFunc {
    private UrlFunc() {
    }

    @ScriptFunction(name = "encodeComponent")
    public static JsonNode encodeComponent(@ScriptParam("value") JsonNode val) throws ScriptExecException {
        if (!val.isTextual()) {
            throw new ScriptExecException("encode component require text value");
        }
        String value = val.textValue();
        return StringUtils.isEmpty(value) ? TextNode.EMPTY_STRING_NODE : TextNode.valueOf(UrlEncoded.encode(value));
    }

    @ScriptFunction(name = "decodeComponent")
    public static JsonNode decodeComponent(@ScriptParam("value") JsonNode val) throws ScriptExecException {
        if (!val.isTextual()) {
            throw new ScriptExecException("decode component require text value");
        }
        String value = val.textValue();
        return StringUtils.isEmpty(value) ? TextNode.EMPTY_STRING_NODE : TextNode.valueOf(UrlEncoded.decode(value));
    }

    @ScriptFunction(name = "parseQuery")
    public static JsonNode parseQuery(@ScriptParam("value") JsonNode val) throws ScriptExecException {
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

    @ScriptFunction(name = "buildQuery")
    public static JsonNode buildQuery(@ScriptParam("value") JsonNode val) throws ScriptExecException {
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
                    appendQueryParam(sb, key, node);
                }
            } else {
                appendQueryParam(sb, key, jsonNode);
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '&') {
            sb.setLength(sb.length() - 1);
        }
        return TextNode.valueOf(sb.toString());
    }

    private static void appendQueryParam(StringBuilder sb, String key, JsonNode value) {
        UrlEncoded.encodeInto(key, StandardCharsets.UTF_8, sb);
        sb.append('=');
        UrlEncoded.encodeInto(JsonUtil.toString(value), StandardCharsets.UTF_8, sb);
        sb.append('&');
    }
}
