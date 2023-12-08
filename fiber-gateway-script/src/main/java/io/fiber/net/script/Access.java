package io.fiber.net.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import io.fiber.net.script.parse.SpelMessage;

public class Access {
    public static JsonNode expandObject(JsonNode addition, JsonNode target) {
        if (target.isObject() && addition.isObject()) {
            ((ObjectNode) target).setAll((ObjectNode) addition);
        }
        return target;
    }

    public static JsonNode expandArray(JsonNode addition, JsonNode target) {
        if (target.isArray() && addition.isContainerNode()) {
            ArrayNode targetArr = (ArrayNode) target;
            if (addition.isArray()) {
                targetArr.addAll((ArrayNode) addition);
            } else {
                addition.elements().forEachRemaining(targetArr::add);
            }
        }
        return target;
    }

    public static JsonNode pushArray(JsonNode addition, JsonNode target) {
        if (target.isArray()) {
            ((ArrayNode) target).add(addition);
        }
        return target;
    }

    public static JsonNode indexGet(JsonNode key, JsonNode parent) {
        if (parent.isArray()) {
            ArrayNode array = (ArrayNode) parent;

            if (!key.isInt()) {
                return MissingNode.getInstance();
            }
            int idx = key.intValue();
            return array.path(idx);
        }

        if (parent.isObject()) {
            if (!key.isTextual()) {
                return MissingNode.getInstance();
            }
            return parent.path(key.textValue());
        }


        if (parent.isTextual()) {
            if (key.isInt()) {
                return MissingNode.getInstance();
            }
            int idx = key.intValue();
            String texted = parent.textValue();
            if (idx < 0 || idx >= texted.length()) {
                return MissingNode.getInstance();
            }
            return TextNode.valueOf(String.valueOf(texted.charAt(idx)));
        }

        return MissingNode.getInstance();
    }

    public static JsonNode indexSet(JsonNode alien, JsonNode key, JsonNode parent) throws ScriptExecException {
        if (parent.isArray()) {
            ArrayNode array = (ArrayNode) parent;

            if (!key.isInt()) {
                throw new ScriptExecException(SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE
                        .formatMessage(key.getNodeType()));
            }
            int idx = key.intValue();
            int size = array.size();
            if (idx >= size || idx < 0) {
                throw new ScriptExecException(SpelMessage.ARRAY_INDEX_OUT_OF_BOUNDS.formatMessage(
                        size, idx
                ));
            }
            array.set(idx, alien);
            return alien;
        }

        if (parent.isObject()) {
            if (!key.isTextual()) {
                throw new ScriptExecException(SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE
                        .formatMessage(key.getNodeType()));
            }

            ((ObjectNode) parent).set(key.textValue(), alien);
            return alien;
        }

        throw new ScriptExecException(SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE
                .formatMessage(parent.getNodeType()));
    }

    public static JsonNode propSet(JsonNode alien, String key, JsonNode parent) throws ScriptExecException {
        if (parent.isObject()) {
            ((ObjectNode) parent).set(key, alien);
            return alien;
        }

        throw new ScriptExecException(SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE
                .formatMessage(parent.getNodeType()));
    }

    public static JsonNode propGet(String key, JsonNode parent) {
        if (parent.isObject()) {
            return parent.path(key);
        }

        if (parent.isTextual() || parent.isArray()) {
            if ("length".equals(key)) {
                return IntNode.valueOf(parent.isTextual() ? parent.textValue().length() : parent.size());
            }
        }

        return MissingNode.getInstance();
    }

}
