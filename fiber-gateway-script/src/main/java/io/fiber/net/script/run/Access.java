package io.fiber.net.script.run;

import io.fiber.net.common.json.*;
import io.fiber.net.script.ScriptExecException;
import io.fiber.net.script.parse.SpelMessage;

public class Access {
    public static ObjectNode expandObject(ObjectNode target, JsonNode addition) {
        if (addition.isObject()) {
            target.setAll((ObjectNode) addition);
        }
        return target;
    }

    public static ArrayNode expandArray(ArrayNode target, JsonNode addition) {
        if (addition.isContainerNode()) {
            if (addition.isArray()) {
                target.addAll((ArrayNode) addition);
            } else {
                addition.elements().forEachRemaining(target::add);
            }
        }
        return target;
    }

    public static ArrayNode pushArray(ArrayNode target, JsonNode addition) {
        target.add(addition);
        return target;
    }

    public static JsonNode indexGet(JsonNode parent, JsonNode key) {
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

    private static void indexSet0(JsonNode parent, JsonNode key, JsonNode alien) throws ScriptExecException {
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
            return;
        }

        if (parent.isObject()) {
            if (!key.isTextual()) {
                throw new ScriptExecException(SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE
                        .formatMessage(key.getNodeType()));
            }

            ((ObjectNode) parent).set(key.textValue(), alien);
            return;
        }

        throw new ScriptExecException(SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE
                .formatMessage(parent.getNodeType()));
    }

    public static JsonNode indexSet(JsonNode parent, JsonNode key, JsonNode alien) throws ScriptExecException {
        indexSet0(parent, key, alien);
        return alien;
    }

    public static JsonNode indexSet1(JsonNode parent, JsonNode key, JsonNode alien) throws ScriptExecException {
        indexSet0(parent, key, alien);
        return parent;
    }

    private static void propSet0(JsonNode parent, JsonNode alien, String key) throws ScriptExecException {
        if (parent.isObject()) {
            ((ObjectNode) parent).set(key, alien);
            return;
        }

        throw new ScriptExecException(SpelMessage.INDEXING_NOT_SUPPORTED_FOR_TYPE
                .formatMessage(parent.getNodeType()));
    }

    public static JsonNode propSet(JsonNode parent, JsonNode alien, String key) throws ScriptExecException {
        propSet0(parent, alien, key);
        return alien;
    }


    public static JsonNode propSet1(JsonNode parent, JsonNode alien, String key) throws ScriptExecException {
        propSet0(parent, alien, key);
        return parent;
    }

    public static JsonNode propGet(JsonNode parent, String key) {
        if (parent.isObject()) {
            return parent.path(key);
        }

        return MissingNode.getInstance();
    }

}
